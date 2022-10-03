package gollorum.signpost.minecraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class CompatConfig {

    public final Waystones waystones;
    public final AntiqueAtlas atlas;

    CompatConfig(ForgeConfigSpec.Builder builder) {
        waystones = new Waystones(builder);
        atlas = new AntiqueAtlas(builder);
    }

    public static class Waystones {

        public final ForgeConfigSpec.BooleanValue allowAsSignTarget;
        public final ForgeConfigSpec.BooleanValue enableTeleport;

        Waystones(ForgeConfigSpec.Builder builder) {
            builder.push("waystones");
            allowAsSignTarget = builder
                .comment("Defines whether palyers can set a Waystones waystone as the target of a Signpost sign.")
                .define("allow_waystones_as_sign_target", true);
            enableTeleport = builder
                .comment("Defines whether players can teleport to a waystone by using a sign post." +
                    "Disable this if you want to force them to use the native Waystones methods like warp scrolls")
                .define("enable_teleport_via_signpost", true);
            builder.pop();
        }

    }

    public static class AntiqueAtlas {

        public final ForgeConfigSpec.BooleanValue shouldAddIcons;
        public final ForgeConfigSpec.BooleanValue enableTeleport;
        public final ForgeConfigSpec.BooleanValue teleportRequiresSignpost;
        public final ForgeConfigSpec.BooleanValue enableDiscovery;

        AntiqueAtlas(ForgeConfigSpec.Builder builder) {
            builder.push("antique_atlas");
            shouldAddIcons = builder
                .comment("Defines whether signpost icons for waystones are automatically added to antique atlases")
                .define("gen_icons", true);
            enableTeleport = builder
                .comment("Defines whether clicking on an automatically generated sign icon will allow the player " +
                    "to teleport to that waystone (if already discovered or 'enable_discovery')")
                .define("enable_teleport", true);
            teleportRequiresSignpost = builder
                .comment("Assuming 'enable_teleport' is on, defines whether the player needs to look directly at" +
                    "a sign post in order to teleport. This means that they have to right click a post for" +
                    "teleportation to work")
                .define("teleport_requires_sign_post", true);
            enableDiscovery = builder
                .comment("Defines whether clicking on an automatically generated sign icon will make the player " +
                    "'discover' that waystone (if not already known)")
                .define("enable_discovery", true);
            builder.pop();
        }

    }

}
