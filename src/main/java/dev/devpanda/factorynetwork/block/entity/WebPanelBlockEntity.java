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

    public WebPanelBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.WEB_PANEL.get(), pos, state);
    }

    public String url() {
        return url;
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
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_URL, url);
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
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
