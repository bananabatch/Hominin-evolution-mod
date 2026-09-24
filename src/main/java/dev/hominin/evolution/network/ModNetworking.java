package dev.hominin.evolution.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String VERSION = "8";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ThreatDisplayPayload.TYPE, ThreatDisplayPayload.STREAM_CODEC,
                ThreatDisplayPayload::handle);
        registrar.playToServer(SocialCommandPayload.TYPE, SocialCommandPayload.STREAM_CODEC,
                SocialCommandPayload::handle);
        registrar.playToServer(FetchRequestPayload.TYPE, FetchRequestPayload.STREAM_CODEC, FetchRequestPayload::handle);
        registrar.playToServer(TakeItemPayload.TYPE, TakeItemPayload.STREAM_CODEC, TakeItemPayload::handle);
        registrar.playToServer(PlaceToolPayload.TYPE, PlaceToolPayload.STREAM_CODEC, PlaceToolPayload::handle);
        registrar.playToServer(BuildActionPayload.TYPE, BuildActionPayload.STREAM_CODEC, BuildActionPayload::handle);
        registrar.playToServer(PileActionPayload.TYPE, PileActionPayload.STREAM_CODEC, PileActionPayload::handle);
        registrar.playToClient(PilePayload.TYPE, PilePayload.STREAM_CODEC, PilePayload::handle);
        registrar.playToClient(BuildMenuPayload.TYPE, BuildMenuPayload.STREAM_CODEC, BuildMenuPayload::handle);
        registrar.playToClient(BlueprintsPayload.TYPE, BlueprintsPayload.STREAM_CODEC, BlueprintsPayload::handle);
        registrar.playToClient(SitesPayload.TYPE, SitesPayload.STREAM_CODEC, SitesPayload::handle);
        registrar.playToServer(ChoosePayload.TYPE, ChoosePayload.STREAM_CODEC, ChoosePayload::handle);
        registrar.playToServer(TradeRequestPayload.TYPE, TradeRequestPayload.STREAM_CODEC, TradeRequestPayload::handle);
        registrar.playToServer(OpenJournalPayload.TYPE, OpenJournalPayload.STREAM_CODEC, OpenJournalPayload::handle);
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
        registrar.playToClient(MoralsPayload.TYPE, MoralsPayload.STREAM_CODEC, MoralsPayload::handle);
        registrar.playToServer(MoralActionPayload.TYPE, MoralActionPayload.STREAM_CODEC, MoralActionPayload::handle);
        registrar.playToClient(ThirstPayload.TYPE, ThirstPayload.STREAM_CODEC, ThirstPayload::handle);
        registrar.playToClient(FocusPayload.TYPE, FocusPayload.STREAM_CODEC, FocusPayload::handle);
        registrar.playToClient(ChoicesPayload.TYPE, ChoicesPayload.STREAM_CODEC, ChoicesPayload::handle);
        registrar.playToClient(DevFlagPayload.TYPE, DevFlagPayload.STREAM_CODEC, DevFlagPayload::handle);
        registrar.playToClient(TradeOpenPayload.TYPE, TradeOpenPayload.STREAM_CODEC, TradeOpenPayload::handle);
        registrar.playToClient(JournalPayload.TYPE, JournalPayload.STREAM_CODEC, JournalPayload::handle);
        registrar.playToClient(ArmsRacePayload.TYPE, ArmsRacePayload.STREAM_CODEC, ArmsRacePayload::handle);
        registrar.playToClient(SkullPosePayload.TYPE, SkullPosePayload.STREAM_CODEC, SkullPosePayload::handle);
        registrar.playToClient(BodyAnimationPayload.TYPE, BodyAnimationPayload.STREAM_CODEC,
                BodyAnimationPayload::handle);
        registrar.playToClient(TipPayload.TYPE, TipPayload.STREAM_CODEC, TipPayload::handle);
        registrar.playToClient(MapPayload.TYPE, MapPayload.STREAM_CODEC, MapPayload::handle);
        registrar.playToServer(MapActionPayload.TYPE, MapActionPayload.STREAM_CODEC, MapActionPayload::handle);
        registrar.playToClient(WaypointPayload.TYPE, WaypointPayload.STREAM_CODEC, WaypointPayload::handle);
        registrar.playToClient(OthersPayload.TYPE, OthersPayload.STREAM_CODEC, OthersPayload::handle);
        registrar.playToServer(OthersActionPayload.TYPE, OthersActionPayload.STREAM_CODEC, OthersActionPayload::handle);
        registrar.playToClient(GiftStockPayload.TYPE, GiftStockPayload.STREAM_CODEC, GiftStockPayload::handle);
        registrar.playToServer(GiftOfferPayload.TYPE, GiftOfferPayload.STREAM_CODEC, GiftOfferPayload::handle);
        registrar.playToClient(CohesionPayload.TYPE, CohesionPayload.STREAM_CODEC, CohesionPayload::handle);
        registrar.playToClient(EncounterPayload.TYPE, EncounterPayload.STREAM_CODEC, EncounterPayload::handle);
        registrar.playToServer(EncounterChoicePayload.TYPE, EncounterChoicePayload.STREAM_CODEC,
                EncounterChoicePayload::handle);
        registrar.playBidirectional(BandNamePayload.TYPE, BandNamePayload.STREAM_CODEC,
                new net.neoforged.neoforge.network.handling.DirectionalPayloadHandler<>(
                        BandNamePayload::handleOnClient, BandNamePayload::handleOnServer));
    }

    private ModNetworking() {
    }
}
