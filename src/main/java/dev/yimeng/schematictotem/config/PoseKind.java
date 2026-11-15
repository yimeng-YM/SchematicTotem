package dev.yimeng.schematictotem.config;

import net.minecraft.text.Text;

/** The display contexts a pose can be set for, with their command / translation ids. */
public enum PoseKind {
    GUI("gui"),
    FIRST_PERSON_RIGHT("firstperson_right"),
    FIRST_PERSON_LEFT("firstperson_left"),
    FIRST_PERSON_MOD_RIGHT("firstperson_mod_right"),
    FIRST_PERSON_MOD_LEFT("firstperson_mod_left"),
    THIRD_PERSON_RIGHT("thirdperson_right"),
    THIRD_PERSON_LEFT("thirdperson_left"),
    GROUND("ground"),
    FIXED("fixed"),
    TOTEM_POP("totem_pop"),
    HEAD("head"),
    ON_SHELF("on_shelf");

    public final String id;

    PoseKind(String id) {
        this.id = id;
    }

    public Text label() {
        return Text.translatable("schematictotem.pose." + id);
    }

    public static PoseKind byId(String id) {
        for (PoseKind kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }

    public TotemConfig.Pose get(TotemConfig.Poses poses) {
        return switch (this) {
            case GUI -> poses.gui;
            case FIRST_PERSON_RIGHT -> poses.firstPersonRight;
            case FIRST_PERSON_LEFT -> poses.firstPersonLeft;
            case FIRST_PERSON_MOD_RIGHT -> poses.firstPersonModRight;
            case FIRST_PERSON_MOD_LEFT -> poses.firstPersonModLeft;
            case THIRD_PERSON_RIGHT -> poses.thirdPersonRight;
            case THIRD_PERSON_LEFT -> poses.thirdPersonLeft;
            case GROUND -> poses.ground;
            case FIXED -> poses.fixed;
            case TOTEM_POP -> poses.totemPop;
            case HEAD -> poses.head;
            case ON_SHELF -> poses.onShelf;
        };
    }

    public TotemConfig.Pose defaults() {
        return switch (this) {
            case GUI -> TotemConfig.Defaults.GUI;
            case FIRST_PERSON_RIGHT -> TotemConfig.Defaults.FIRST_PERSON_RIGHT;
            case FIRST_PERSON_LEFT -> TotemConfig.Defaults.FIRST_PERSON_LEFT;
            case FIRST_PERSON_MOD_RIGHT -> TotemConfig.Defaults.FIRST_PERSON_MOD_RIGHT;
            case FIRST_PERSON_MOD_LEFT -> TotemConfig.Defaults.FIRST_PERSON_MOD_LEFT;
            case THIRD_PERSON_RIGHT -> TotemConfig.Defaults.THIRD_PERSON_RIGHT;
            case THIRD_PERSON_LEFT -> TotemConfig.Defaults.THIRD_PERSON_LEFT;
            case GROUND -> TotemConfig.Defaults.GROUND;
            case FIXED -> TotemConfig.Defaults.FIXED;
            case TOTEM_POP -> TotemConfig.Defaults.TOTEM_POP;
            case HEAD -> TotemConfig.Defaults.HEAD;
            case ON_SHELF -> TotemConfig.Defaults.ON_SHELF;
        };
    }

    public boolean rightHand() {
        return this == FIRST_PERSON_RIGHT || this == FIRST_PERSON_MOD_RIGHT || this == THIRD_PERSON_RIGHT;
    }

    /** The same pose for the other hand, or null for contexts without hands. */
    public PoseKind otherHand() {
        return switch (this) {
            case FIRST_PERSON_RIGHT -> FIRST_PERSON_LEFT;
            case FIRST_PERSON_LEFT -> FIRST_PERSON_RIGHT;
            case FIRST_PERSON_MOD_RIGHT -> FIRST_PERSON_MOD_LEFT;
            case FIRST_PERSON_MOD_LEFT -> FIRST_PERSON_MOD_RIGHT;
            case THIRD_PERSON_RIGHT -> THIRD_PERSON_LEFT;
            case THIRD_PERSON_LEFT -> THIRD_PERSON_RIGHT;
            default -> null;
        };
    }

    /**
     * Copies this hand's pose to the other hand so both look alike. Vanilla mirrors left-hand transforms by negating
     * x offset and y/z rotation. In first person (vanilla's, and Punchy's, whose hand frames are mirror images too) that
     * mirror already gives symmetric hands, so the values are copied as they are. In third person both figures should
     * face the way the player faces rather than mirror each other, so the copy is pre-mirrored there.
     */
    public void copyToOtherHand(TotemConfig.Poses poses) {
        PoseKind other = otherHand();
        if (other == null) return;
        TotemConfig.Pose from = get(poses), to = other.get(poses);
        boolean mirror = this == THIRD_PERSON_RIGHT || this == THIRD_PERSON_LEFT;
        to.rotation = new float[]{from.rotation[0], mirror ? -from.rotation[1] : from.rotation[1], mirror ? -from.rotation[2] : from.rotation[2]};
        to.translation = new float[]{mirror ? -from.translation[0] : from.translation[0], from.translation[1], from.translation[2]};
        to.scale = from.scale;
    }

    public void reset(TotemConfig.Poses poses) {
        TotemConfig.Pose pose = get(poses), d = defaults();
        pose.rotation = d.rotation.clone();
        pose.translation = d.translation.clone();
        pose.scale = d.scale;
    }
}
