import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads freshly compiled classes into a running dev client.
 *
 * <p>Usage: {@code java Hotswap.java <port> <liveDir>=<freshDir> ...}
 *
 * <p>{@code liveDir} is the class directory the client loads from,
 * {@code freshDir} the one the hotswap compile writes to. The client's own
 * directory is never rebuilt while it runs; a full recompile there deletes
 * classes the game has not loaded yet. Instead, every class that differs is
 * redefined in the client if it is loaded, and then copied over the live
 * file, so that the live directory always matches what the client runs.
 *
 * <p>A plain HotSpot VM only accepts changed method bodies. New or removed
 * methods and fields are refused; those classes are reported and left alone,
 * and the change needs a restart.
 */
public final class Hotswap {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        List<Change> changes = new ArrayList<>();
        for (String pair : Arrays.copyOfRange(args, 1, args.length)) {
            String[] dirs = pair.split("=", 2);
            collect(Path.of(dirs[0]), Path.of(dirs[1]), changes);
        }
        if (changes.isEmpty()) {
            System.out.println("hotswap: nothing changed");
            return;
        }

        VirtualMachine vm = attach(port);
        try {
            int redefined = 0;
            int copied = 0;
            List<String> refused = new ArrayList<>();
            for (Change change : changes) {
                List<ReferenceType> loaded = vm.classesByName(change.name);
                if (!loaded.isEmpty()) {
                    Map<ReferenceType, byte[]> map = new HashMap<>();
                    for (ReferenceType type : loaded) {
                        map.put(type, change.bytes);
                    }
                    try {
                        vm.redefineClasses(map);
                        redefined++;
                    } catch (UnsupportedOperationException | LinkageError e) {
                        refused.add(change.name + " (" + e.getClass().getSimpleName() + ")");
                        continue;
                    }
                }
                Files.createDirectories(change.live.getParent());
                Files.copy(change.fresh, change.live, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            System.out.println("hotswap: " + redefined + " classes redefined, "
                    + (copied - redefined) + " not yet loaded and updated on disk");
            if (!refused.isEmpty()) {
                System.out.println("hotswap: refused, restart needed for:");
                refused.forEach(name -> System.out.println("  " + name));
                System.exit(2);
            }
        } finally {
            vm.dispose();
        }
    }

    private record Change(String name, Path live, Path fresh, byte[] bytes) {
    }

    private static void collect(Path live, Path fresh, List<Change> out) throws IOException {
        if (!Files.isDirectory(fresh)) {
            return;
        }
        try (Stream<Path> files = Files.walk(fresh)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                Path relative = fresh.relativize(file);
                String name = relative.toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                // Mixin classes are applied at load time, never loaded as such.
                if (name.contains(".mixin.")) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                Path target = live.resolve(relative);
                if (Files.exists(target) && Arrays.equals(bytes, Files.readAllBytes(target))) {
                    continue;
                }
                out.add(new Change(name, target, file, bytes));
            }
        }
    }

    private static VirtualMachine attach(int port) throws Exception {
        AttachingConnector socket = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.transport().name().equals("dt_socket"))
                .findFirst()
                .orElseThrow();
        Map<String, Connector.Argument> arguments = socket.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(Integer.toString(port));
        try {
            return socket.attach(arguments);
        } catch (IOException e) {
            System.out.println("hotswap: no client listening on port " + port
                    + " - is runClient running?");
            System.exit(1);
            throw e;
        }
    }
}
