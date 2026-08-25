package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Der Fabricator: Er baut, was das Netz bestellt.
 *
 * <p><b>Keine Muster-Items.</b> Was er bauen kann, weiß das Spiel bereits —
 * jedes Werkbank-Rezept steht im Server. Ein Netz, das sich seine Rezepte
 * erst auf Papierschnipsel schreiben lässt, verlangt Arbeit für eine
 * Auskunft, die schon dasteht.
 *
 * <p><b>Keine BlockEntity.</b> Er hält nichts: Der Auftrag lebt am
 * Controller, die Zutaten im Speicher, das Rezept im Server. Was er
 * beisteuert, ist die Erlaubnis, dass gebaut wird — und wie viele im Netz
 * hängen, entscheidet, wie viele Schritte je Takt geschehen. Wer schneller
 * fertigen will, stellt einen zweiten hin.
 *
 * <p>Er kostet einen Kanal und Strom wie jedes andere Gerät am Netz.
 */
public class FabricatorBlock extends Block {

    public static final MapCodec<FabricatorBlock> CODEC = simpleCodec(FabricatorBlock::new);

    public FabricatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
