package com.ogtenzohd.cclogistics.client;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ui.ForemenHutScreen;
import com.ogtenzohd.cclogistics.client.gui.FreightDepotScreen;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.ui.LogisticsControllerScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CreateColonyLogistics.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
		
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CCLRegistration.LOGISTICS_CONTROLLER_MENU.get(), LogisticsControllerScreen::new);
        event.register(CCLRegistration.FOREMEN_HUT_MENU.get(), ForemenHutScreen::new);
        event.register(CCLRegistration.FREIGHT_DEPOT_MENU.get(), FreightDepotScreen::new);
    }
}