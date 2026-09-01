package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Eine Web-Fläche an der Wand.
 *
 * <p>Sie hält eine Adresse und sonst nichts. Der Browser dahinter gehört ihr
 * nicht: Er lebt in {@code WebPanels}, wird beim Zeichnen geholt und macht zu,
 * wenn niemand mehr hinsieht. Das ist Absicht — an einer Blockentität hängt
 * ein Lebenslauf aus Chunkgrenzen und Weltwechseln, und ein Chromium daran zu
 * knüpfen hieße, ihn dreimal richtig hinzubekommen statt einmal.
 *
 * <p><b>Was zu sehen ist, entscheidet der Spieler.</b> Deshalb eine Adresse
 * als Feld und keine feste Ansicht. Wie er sie einträgt, ist noch offen; bis
 * dahin steht hier eine Startseite, damit die Fläche etwas zeigt.
 */
public class WebPanelBlockEntity extends BlockEntity {

    private static final String KEY_URL = "Url";
    private static final String KEY_NAME = "PanelName";

    /**
     * Womit eine frisch gesetzte Fläche anfängt.
     *
     * <p>Leer heißt: die mitgelieferte Startseite. Sie ist da, auch wenn
     * niemand eine Verbindung hat, und sie zeigt sofort, ob der Weg bis
     * hierher trägt. Wo sie liegt, weiß nur der Client — deshalb ein leerer
     * Wert und keine Adresse, die der Server nicht kennen kann.
     */
    public static final String DEFAULT_URL = "";

    private String url = DEFAULT_URL;

    /**
     * Wie diese Tafel heißt.
     *
     * <p><b>Vergeben wird er im Programm, nicht am Gegenstand.</b> So wie ein
     * Display über seinen Namen angesprochen wird, gehört auch eine Web-Fläche
     * benannt, bevor ein Programm ihr etwas zu zeigen geben kann. Der Weg über
     * einen Amboss stand hier einmal und war falsch: Er benennt einen
     * Gegenstand, und wer die Tafel abbaut und neu setzt, verliert den Bezug.
     *
     * <p>Der Name steht danach im Protokoll und in Chromiums Liste unter dem
     * Fernwartungsport.
     */
    private String name = "";

    public WebPanelBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.WEB_PANEL.get(), pos, state);
    }

    public String url() {
        return url;
    }

    public String name() {
        return name;
    }

    /** Benennt die Tafel — gerufen aus dem Programm. */
    public void setName(String name) {
        this.name = name == null ? "" : name;
        setChanged();
    }

    public void setUrl(String url) {
        this.url = url == null ? DEFAULT_URL : url;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_URL)) {
            url = tag.getString(KEY_URL);
        }
        name = tag.getString(KEY_NAME);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_URL, url);
        tag.putString(KEY_NAME, name);
    }

    /**
     * Was der Client beim Laden des Chunks bekommt.
     *
     * <p>Ohne das stünde die Fläche beim Betreten der Welt auf der Startseite,
     * bis irgendetwas anderes ein Update auslöst.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(KEY_URL, url);
        tag.putString(KEY_NAME, name);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
