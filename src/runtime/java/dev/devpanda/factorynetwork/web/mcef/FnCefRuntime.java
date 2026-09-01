package dev.devpanda.factorynetwork.web.mcef;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.slf4j.Logger;

import java.io.File;

/**
 * Chromium ohne MCEF — die Dev-Fassung.
 *
 * <p><b>Was diese Klasse ist und was sie nicht ist.</b> Sie fährt die selbst
 * gebaute Laufzeitumgebung hoch, damit der neue Browser- und Eingabepfad im
 * Spiel geprüft werden kann. Sie ist <i>kein</i> Auslieferungsweg: Es gibt
 * keinen Download, keine Prüfsummen, keine Wächter über fremde Prozesse. Der
 * Ordner muss dasein, sonst sagt sie es und hört auf.
 *
 * <pre>
 *   pwsh -File tools/runtime/build-jcef.ps1
 *   ./gradlew runClient
 * </pre>
 *
 * <p><b>Alles läuft im Renderthread — auch das Hochfahren.</b> CEF verlangt,
 * dass Initialisierung, Nachrichtenschleife und Herunterfahren in demselben
 * Thread stattfinden, und unser Renderpfad verlangt, dass es der Renderthread
 * ist: {@code onPaint} kommt dort an, wo gepumpt wird, und die Freigabe der
 * Textur hängt daran. Deshalb wird hier nichts beim Start des Spiels
 * angestoßen, sondern beim ersten Bedarf — und der entsteht im Renderthread.
 */
public final class FnCefRuntime {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Wo die gebaute Laufzeitumgebung liegt.
     *
     * <p>Gesetzt vom Buildskript bei jedem Lauf. Der Rückfall auf einen
     * relativen Pfad ist für den Fall gedacht, dass jemand den Client von Hand
     * startet.
     */
    private static final String DIR_PROPERTY = "fn.runtime.dir";

    /**
     * Ob die Fehlersuche mitläuft: {@code -Dfn.cef.trace=true}.
     *
     * <p>Sie hängt sich in die Standardfehlerausgabe und schreibt jede Taste
     * mit. Beides ist im Alltag nur Lärm und im Zweifelsfall das Einzige, was
     * hilft — deshalb ein Schalter und keine Entscheidung für immer.
     */
    static final boolean TRACE = Boolean.getBoolean("fn.cef.trace");

    private static CefApp app;
    private static CefClient client;
    private static Thread owner;
    private static boolean failed;
    private static String failure;

    /**
     * Fährt hoch, falls nötig, und gibt den Thread zurück, dem CEF gehört.
     *
     * @throws IllegalStateException wenn es nicht geht — der Text nennt den
     *         Grund und ist für Menschen gedacht
     */
    public static synchronized void ensureStarted() {
        if (app != null) {
            return;
        }
        if (failed) {
            throw new IllegalStateException(failure);
        }
        try {
            start();
        } catch (Throwable broken) {
            failed = true;
            failure = broken.getMessage() == null ? broken.toString() : broken.getMessage();
            LOG.error("Die eigene Laufzeitumgebung kam nicht hoch", broken);
            throw new IllegalStateException(failure, broken);
        }
    }

    private static void start() {
        File dir = runtimeDir();
        owner = Thread.currentThread();
        LOG.info("Eigene Laufzeitumgebung: {} — im Thread {}", dir, owner.getName());
        if (TRACE) {
            captureStandardError();
        }

        // <b>Die Bibliotheken lädt java-cef selbst, und das muss so sein.</b>
        // Der erste Entwurf setzte hier einen eigenen Ladeweg über
        // SystemBootstrap.setLoader und System.load mit absolutem Pfad. Das
        // lädt die Datei zwar, nützt aber nichts: Native Methoden findet die
        // JVM nur in Bibliotheken, die der Klassenlader der jeweiligen Klasse
        // geladen hat. Gemessen im Spiel —
        //
        //   CefApp        cpw.mods.cl.ModuleClassLoader
        //   FnCefRuntime  cpw.mods.modlauncher.TransformingClassLoader
        //
        // — und ein System.load aus unserem Code bindet an den zweiten. Die
        // Folge ist ein UnsatisfiedLinkError beim ersten nativen Aufruf,
        // obwohl die Bibliothek längst im Prozess steht.
        //
        // Deshalb bleibt der Vorgabeweg: java-cef ruft System.loadLibrary aus
        // seiner eigenen Klasse, und dann stimmt der Lader. Gefunden wird die
        // Datei über java.library.path — auf Windows speist der sich aus dem
        // PATH, und den setzt das Buildskript für diesen Lauf.

        // <b>Vor CefApp.startup, nicht danach.</b> Der Wächter klammert den
        // eigenen Prozess ein, und Kindprozesse erben die Zugehörigkeit. Wer
        // erst nach dem Start klammert, klammert die schon gestarteten Helfer
        // nicht mehr ein.
        dev.devpanda.factorynetwork.web.ProcessGuard.install();

        if (!CefApp.startup(new String[] {})) {
            throw new IllegalStateException("CefApp.startup schlug fehl");
        }

        // Vor dem ersten getInstance und aus dem Thread, in dem danach gepumpt
        // wird. Ohne das schiebt CefApp Initialisierung und Schleife auf AWTs
        // Ereignisthread — die Bilder kämen dann im falschen Thread an, und der
        // Takt wäre bei dreißig gedeckelt.
        CefApp.useCallingThread();

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.log_file = new File(gameDirectory(), "logs/fn-cef.log").getAbsolutePath();

        // Ausdrücklich gesetzt, nicht geraten: Upstream leitet den Pfad des
        // Hilfsprozesses sonst aus java.library.path ab, und der zeigt hier
        // nicht auf unseren Ordner.
        settings.browser_subprocess_path = new File(dir, "jcef_helper.exe").getAbsolutePath();
        settings.resources_dir_path = dir.getAbsolutePath();
        settings.locales_dir_path = new File(dir, "locales").getAbsolutePath();

        // Ohne eigenen Zwischenspeicher warnt CEF vor unbeabsichtigtem
        // Verhalten, wenn zwei Anwendungen sich denselben Vorgabepfad teilen.
        File cache = new File(gameDirectory(), "fn-cef-cache");
        settings.root_cache_path = cache.getAbsolutePath();
        settings.cache_path = cache.getAbsolutePath();

        app = CefApp.getInstance(settings);
        client = app.createClient();
        LOG.info("Chromium ist da: {}", app.getVersion());
    }

    /**
     * Eine Runde der Nachrichtenschleife.
     *
     * <p>Aus dem Renderthread, einmal je Bild. Alles, was CEF zu melden hat —
     * auch {@code onPaint} —, kommt in diesem Aufruf und damit in diesem
     * Thread.
     *
     * <p>Nicht {@code doMessageLoopWork}: Das ist der Weg für Anwendungen ohne
     * eigene Schleife und reicht die Arbeit an AWT weiter. CEF ruft es
     * außerdem selbst zurück, um eine Runde zu erbitten — die erste noch
     * mitten aus der Initialisierung heraus.
     */
    public static void pump() {
        CefApp running = app;
        if (running != null) {
            running.doMessageLoopWorkNow();
        }
    }

    public static synchronized CefApp app() {
        ensureStarted();
        return app;
    }

    public static synchronized CefClient client() {
        ensureStarted();
        return client;
    }

    /** Der Thread, dem CEF gehört — null, solange nichts läuft. */
    public static Thread owner() {
        return owner;
    }

    public static synchronized boolean running() {
        return app != null;
    }

    /**
     * Fährt herunter.
     *
     * <p>Im selben Thread wie das Hochfahren, und mit Weiterpumpen: CEF
     * arbeitet das Schließen über seine eigene Schleife ab, und wer nach
     * {@code dispose()} nicht mehr pumpt, lässt die Hilfsprozesse stehen.
     */
    public static synchronized void shutdown() {
        if (app == null) {
            return;
        }
        CefApp closing = app;
        app = null;
        client = null;
        try {
            closing.dispose();
            for (int i = 0; i < 200 && CefApp.getState() != CefApp.CefAppState.TERMINATED; i++) {
                closing.doMessageLoopWorkNow();
                Thread.sleep(5);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable broken) {
            LOG.warn("Beim Herunterfahren ging etwas schief", broken);
        }
        LOG.info("Chromium ist unten: {}", CefApp.getState());
    }

    /**
     * Schreibt mit, was auf die Standardfehlerausgabe geht.
     *
     * <p><b>Wozu, obwohl es ein Protokoll gibt.</b> Wenn eine Ausnahme aus
     * einem Rückruf von Chromium heraus fliegt, meldet der native Teil sie
     * über {@code ExceptionDescribe}: Die Kopfzeile geht an die nackte
     * Fehlerausgabe, der Stapel über {@code printStackTrace} an
     * {@code System.err} — und den hat das Spiel längst umgebogen. Übrig
     * bleibt ein „Exception in thread" ohne alles dahinter, und damit lässt
     * sich nichts anfangen.
     *
     * <p>Hier wird deshalb ein zweiter Weg danebengelegt, der niemandem
     * gehört: eine Datei unter {@code logs/}. Was durchgeht, geht weiterhin
     * auch dorthin, wo es vorher hinging.
     */
    private static void captureStandardError() {
        try {
            File file = new File(gameDirectory(), "logs/fn-cef-stderr.log");
            file.getParentFile().mkdirs();
            java.io.PrintStream original = System.err;
            java.io.OutputStream toFile = new java.io.FileOutputStream(file, true);
            System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
                @Override
                public void write(int b) throws java.io.IOException {
                    toFile.write(b);
                    original.write(b);
                }

                @Override
                public void flush() throws java.io.IOException {
                    toFile.flush();
                    original.flush();
                }
            }, true));
            LOG.info("Fehlerausgabe wird zusätzlich nach {} geschrieben", file);
        } catch (Throwable broken) {
            LOG.warn("Fehlerausgabe konnte nicht mitgeschrieben werden", broken);
        }
    }

    private static File runtimeDir() {
        String configured = System.getProperty(DIR_PROPERTY);
        File dir = configured != null
                ? new File(configured)
                : new File(gameDirectory(), "../tools/runtime/build/out/windows-x86_64");
        if (!new File(dir, "jcef.jar").isFile()) {
            throw new IllegalStateException("Keine Laufzeitumgebung unter " + dir
                    + " — erst: pwsh -File tools/runtime/build-jcef.ps1");
        }
        return dir;
    }

    private static File gameDirectory() {
        try {
            return Minecraft.getInstance().gameDirectory;
        } catch (Throwable outsideTheGame) {
            return new File(".");
        }
    }

    private FnCefRuntime() {
    }
}
