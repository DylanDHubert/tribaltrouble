package com.oddlabs.tt.vault;

import com.oddlabs.tt.gui.CheckBox;
import com.oddlabs.tt.gui.Form;
import com.oddlabs.tt.gui.Group;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.HorizButton;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.PulldownButton;
import com.oddlabs.tt.gui.PulldownItem;
import com.oddlabs.tt.gui.PulldownMenu;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.gui.Slider;
import com.oddlabs.tt.render.Renderer;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;

import static com.oddlabs.tt.gui.Placement.BOTTOM_LEFT;
import static com.oddlabs.tt.gui.Placement.RIGHT_MID;

/**
 * SKINNED CONTROL PANEL FOR BROWSING / PLAYING / ROTATING VAULT SPRITES.
 */
public final class VaultForm extends Form {
    private final @NonNull VaultController controller;
    private final @NonNull Label spriteLabel;
    private final @NonNull Label metaLabel;
    private final @NonNull Label statusLabel;
    private final @NonNull PulldownMenu<Integer> animMenu;
    private final @NonNull PulldownButton<Integer> animButton;
    private final @NonNull CheckBox playBox;
    private final @NonNull Slider scrubSlider;
    private boolean updatingUi;
    private @NonNull String @NonNull [] lastAnimNames = new String[0];

    public VaultForm(@NonNull GUIRoot gui_root, @NonNull VaultController controller) {
        super("Vault");
        this.controller = controller;

        Label head = new Label("Sprite Vault", Skin.getSkin().getHeadlineFont());
        addChild(head);
        head.place();

        spriteLabel = new Label("", Skin.getSkin().getEditFont(), 280);
        addChild(spriteLabel);
        spriteLabel.place(head, BOTTOM_LEFT);

        metaLabel = new Label("", Skin.getSkin().getEditFont(), 280);
        addChild(metaLabel);
        metaLabel.place(spriteLabel, BOTTOM_LEFT);

        statusLabel = new Label("", Skin.getSkin().getEditFont(), 280);
        addChild(statusLabel);
        statusLabel.place(metaLabel, BOTTOM_LEFT);

        Group nav = new Group();
        HorizButton prev = new HorizButton("< Prev", 90);
        HorizButton next = new HorizButton("Next >", 90);
        nav.addChild(prev);
        nav.addChild(next);
        prev.place();
        next.place(prev, RIGHT_MID);
        nav.compileCanvas();
        addChild(nav);
        nav.place(statusLabel, BOTTOM_LEFT);
        prev.addMouseClickListener((_, _, _, _) -> controller.prevSprite());
        next.addMouseClickListener((_, _, _, _) -> controller.nextSprite());

        Label animCaption = new Label("Animation", Skin.getSkin().getEditFont());
        addChild(animCaption);
        animCaption.place(nav, BOTTOM_LEFT);

        animMenu = new PulldownMenu<>();
        animButton = new PulldownButton<>(gui_root, animMenu, 220);
        addChild(animButton);
        animButton.place(animCaption, BOTTOM_LEFT);
        animMenu.addItemChosenListener((_, index) -> {
            if (!updatingUi && index >= 0)
                controller.setAnimIndex(index);
        });

        Group animNav = new Group();
        HorizButton prevAnim = new HorizButton("< Anim", 90);
        HorizButton nextAnim = new HorizButton("Anim >", 90);
        animNav.addChild(prevAnim);
        animNav.addChild(nextAnim);
        prevAnim.place();
        nextAnim.place(prevAnim, RIGHT_MID);
        animNav.compileCanvas();
        addChild(animNav);
        animNav.place(animButton, BOTTOM_LEFT);
        prevAnim.addMouseClickListener((_, _, _, _) -> controller.prevAnim());
        nextAnim.addMouseClickListener((_, _, _, _) -> controller.nextAnim());

        playBox = new CheckBox(true, "Play");
        addChild(playBox);
        playBox.place(animNav, BOTTOM_LEFT);
        playBox.addCheckBoxListener(marked -> {
            if (!updatingUi)
                controller.setPlaying(marked);
        });

        Label scrubCaption = new Label("Scrub", Skin.getSkin().getEditFont());
        addChild(scrubCaption);
        scrubCaption.place(playBox, BOTTOM_LEFT);

        scrubSlider = new Slider(220, 0, 200, 0);
        addChild(scrubSlider);
        scrubSlider.place(scrubCaption, BOTTOM_LEFT);
        scrubSlider.addValueListener(value -> {
            if (!updatingUi)
                controller.setAnimTicks(value / 10f);
        });

        Group util = new Group();
        HorizButton lod = new HorizButton("LOD", 70);
        HorizButton tex = new HorizButton("Tex", 70);
        HorizButton reset = new HorizButton("Reset", 70);
        util.addChild(lod);
        util.addChild(tex);
        util.addChild(reset);
        lod.place();
        tex.place(lod, RIGHT_MID);
        reset.place(tex, RIGHT_MID);
        util.compileCanvas();
        addChild(util);
        util.place(scrubSlider, BOTTOM_LEFT);
        lod.addMouseClickListener((_, _, _, _) -> controller.cycleLod());
        tex.addMouseClickListener((_, _, _, _) -> controller.cycleTexture());
        reset.addMouseClickListener((_, _, _, _) -> controller.resetView());

        Label help = new Label("Drag L: orbit  R: yaw  Scroll: zoom", Skin.getSkin().getEditFont(), 280);
        addChild(help);
        help.place(util, BOTTOM_LEFT);

        HorizButton quit = new HorizButton("Quit", 100);
        addChild(quit);
        quit.place(help, BOTTOM_LEFT);
        quit.addMouseClickListener((_, _, _, _) -> Renderer.shutdown());

        compileCanvas();
        setPos(16, 16);
        refresh();
    }

    public void refresh() {
        updatingUi = true;
        try {
            VaultEntry entry = controller.getCurrentEntry();
            spriteLabel.set(entry != null
                    ? (controller.getEntryIndex() + 1) + "/" + controller.getCatalog().size() + "  " + entry.displayName()
                    : "(none)");
            metaLabel.set("LOD " + (controller.getLodIndex() + 1) + "/" + Math.max(1, controller.getNumLods())
                    + "   Tex " + (controller.getTexIndex() + 1) + "/" + Math.max(1, controller.getNumTextures())
                    + (entry != null ? "   scale " + entry.scale() : ""));
            String status = controller.getStatusMessage();
            statusLabel.set(status != null ? status : entry != null ? entry.binspritePath() : "");

            syncAnimMenu();
            if (playBox.isMarked() != controller.isPlaying())
                playBox.setMarked(controller.isPlaying());
            int scrub = Math.min(200, Math.max(0, Math.round(controller.getAnimTicks() * 10f)));
            if (scrubSlider.getValue() != scrub)
                scrubSlider.setValue(scrub);
        } finally {
            updatingUi = false;
        }
    }

    private void syncAnimMenu() {
        // CLOSE OPEN MENU BEFORE MUTATING ITEMS — AVOIDS FOCUS / LAYOUT GLITCHES.
        if (animMenu.getParent() != null)
            animMenu.remove();

        String[] names = controller.getAnimationNames();
        boolean same = Arrays.equals(names, lastAnimNames);
        if (!same) {
            animMenu.clear();
            if (names.length == 0) {
                animMenu.addItem(new PulldownItem<>("(none)", -1));
            } else {
                for (int i = 0; i < names.length; i++)
                    animMenu.addItem(new PulldownItem<>(names[i], i));
            }
            lastAnimNames = Arrays.copyOf(names, names.length);
        }

        int index = names.length == 0 ? 0 : Math.min(controller.getAnimIndex(), names.length - 1);
        // SILENT SELECT — chooseItem() WOULD FIRE LISTENERS AND STEAL FOCUS FROM BUTTONS.
        if (animMenu.getSize() > 0)
            animButton.setSelected(index);
    }

    @Override
    public void cancel() {
        Renderer.shutdown();
    }
}
