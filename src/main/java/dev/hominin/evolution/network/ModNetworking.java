package dev.hominin.evolution.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String VERSION = "5";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ThreatDisplayPayload.TYPE, ThreatDisplayPayload.STREAM_CODEC,
                ThreatDisplayPayload::handle);
        registrar.playToServer(SocialCommandPayload.TYPE, SocialCommandPayload.STREAM_CODEC,
                SocialCommandPayload::handle);
        registrar.playToServer(FetchRequestPayload.TYPE, FetchRequestPayload.STREAM_CODEC, FetchRequestPayload::handle);
        registrar.playToServer(TakeItemPayload.TYPE, TakeItemPayload.STREAM_CODEC, TakeItemPayload::handle);
        registrar.playToServer(ViewInventoryPayload.TYPE, ViewInventoryPayload.STREAM_CODEC,
                ViewInventoryPayload::handle);
        registrar.playToClient(MemberInventoryPayload.TYPE, MemberInventoryPayload.STREAM_CODEC,
                MemberInventoryPayload::handle);
        registrar.playToServer(ClimbPayload.TYPE, ClimbPayload.STREAM_CODEC, ClimbPayload::handle);
        registrar.playToServer(ItemInteractPayload.TYPE, ItemInteractPayload.STREAM_CODEC, ItemInteractPayload::handle);
        registrar.playToServer(ThinkPayload.TYPE, ThinkPayload.STREAM_CODEC, ThinkPayload::handle);
        registrar.playToServer(DrinkPayload.TYPE, DrinkPayload.STREAM_CODEC, DrinkPayload::handle);
        registrar.playToServer(SharpenStickPayload.TYPE, SharpenStickPayload.STREAM_CODEC,
                SharpenStickPayload::handle);
        registrar.playToServer(KnappingChoicePayload.TYPE, KnappingChoicePayload.STREAM_CODEC,
                KnappingChoicePayload::handle);
        registrar.playToClient(ChecklistPayload.TYPE, ChecklistPayload.STREAM_CODEC, ChecklistPayload::handle);
        registrar.playToClient(CutsceneStartPayload.TYPE, CutsceneStartPayload.STREAM_CODEC,
                CutsceneStartPayload::handle);
        registrar.playToClient(CutsceneArrivalPayload.TYPE, CutsceneArrivalPayload.STREAM_CODEC,
                CutsceneArrivalPayload::handle);
        registrar.playToClient(OpenKnappingPayload.TYPE, OpenKnappingPayload.STREAM_CODEC,
                OpenKnappingPayload::handle);
        registrar.playToClient(ClimbStatePayload.TYPE, ClimbStatePayload.STREAM_CODEC, ClimbStatePayload::handle);
        registrar.playToClient(RebirthPayload.TYPE, RebirthPayload.STREAM_CODEC, RebirthPayload::handle);
        registrar.playToClient(TiredApesPayload.TYPE, TiredApesPayload.STREAM_CODEC, TiredApesPayload::handle);
        registrar.playToClient(StagePayload.TYPE, StagePayload.STREAM_CODEC, StagePayload::handle);
        registrar.playToClient(MemberInfoPayload.TYPE, MemberInfoPayload.STREAM_CODEC, MemberInfoPayload::handle);
        registrar.playToClient(ThirstPayload.TYPE, ThirstPayload.STREAM_CODEC, ThirstPayload::handle);
        registrar.playToClient(FocusPayload.TYPE, FocusPayload.STREAM_CODEC, FocusPayload::handle);
        registrar.playToClient(ArmsRacePayload.TYPE, ArmsRacePayload.STREAM_CODEC, ArmsRacePayload::handle);
        registrar.playToClient(BodyAnimationPayload.TYPE, BodyAnimationPayload.STREAM_CODEC,
                BodyAnimationPayload::handle);
    }

    private ModNetworking() {
    }
}
