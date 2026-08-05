package com.oddlabs.tt.form;

import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.gui.ColorPreviewBox;
import com.oddlabs.tt.gui.ColorWheel;
import com.oddlabs.tt.gui.Form;
import com.oddlabs.tt.gui.Group;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.HorizButton;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.MouseButton;
import com.oddlabs.tt.gui.OKButton;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.gui.Slider;
import com.oddlabs.tt.util.Utils;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ResourceBundle;
import java.util.function.Consumer;

import static com.oddlabs.tt.gui.Placement.BOTTOM_LEFT;
import static com.oddlabs.tt.gui.Placement.BOTTOM_MID;
import static com.oddlabs.tt.gui.Placement.RIGHT_MID;

public final class PlayerColorPickerForm extends Form {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(PlayerColorPickerForm.class.getName());
    private static final int WHEEL_SIZE = 140;
    private static final int SLIDER_WIDTH = 220;
    private static final int BRIGHTNESS_STEPS = 100;

    private static @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(bundle, key, args);
    }

    /**
     * @param on_color_changed   called whenever the local color changes, to refresh the lobby UI immediately
     * @param on_ok_network_sync called once with the final color when OK is pressed, to (best-effort) broadcast
     *                           it to other players over the network; may be null if network sync isn't available
     */
    public PlayerColorPickerForm(@NonNull GUIRoot gui_root, int player_slot, @NonNull Runnable on_color_changed,
            @Nullable Consumer<Vector4f> on_ok_network_sync) {
        super(i18n("caption"));

        Group content = new Group();
        addChild(content);

        Label player_label = new Label(i18n("player", Integer.toString(player_slot + 1)),
                Skin.getSkin().getEditFont()).setColor(Settings.getSettings().team_colours[player_slot]);
        content.addChild(player_label);

        ColorPreviewBox preview = new ColorPreviewBox(24);
        content.addChild(preview);

        ColorWheel color_wheel = new ColorWheel(WHEEL_SIZE);
        content.addChild(color_wheel);

        Label brightness_label = new Label(i18n("brightness"), Skin.getSkin().getEditFont());
        content.addChild(brightness_label);
        Slider brightness_slider = new Slider(SLIDER_WIDTH, 0, BRIGHTNESS_STEPS, BRIGHTNESS_STEPS);
        content.addChild(brightness_slider);

        HorizButton reset_button = new HorizButton(i18n("reset"), 80);
        content.addChild(reset_button);

        HorizButton ok_button = new OKButton(70);
        content.addChild(ok_button);

        final boolean[] updating = {false};

        Runnable applyColor = () -> {
            if (updating[0]) {
                return;
            }
            Vector4f color = color_wheel.getColor();
            Settings.getSettings().team_colours[player_slot] = color;
            preview.setColor(color);
            player_label.setColor(color);
            on_color_changed.run();
        };

        Runnable refreshFromSettings = () -> {
            updating[0] = true;
            Vector4fc current = Settings.getSettings().team_colours[player_slot];
            color_wheel.setColor(current);
            float[] hsb = java.awt.Color.RGBtoHSB((int) (current.x() * 255), (int) (current.y() * 255),
                    (int) (current.z() * 255), null);
            brightness_slider.setValue((int) (hsb[2] * BRIGHTNESS_STEPS));
            color_wheel.setBrightness(hsb[2]);
            preview.setColor(color_wheel.getColor());
            player_label.setColor(current);
            updating[0] = false;
        };

        color_wheel.addColorChangeListener(applyColor);
        brightness_slider.addValueListener(value -> {
            color_wheel.setBrightness(value / (float) BRIGHTNESS_STEPS);
            applyColor.run();
        });

        reset_button.addMouseClickListener((_, _, _, _) -> {
            Settings.getSettings().team_colours[player_slot] = new Vector4f(Settings.DEFAULT_TEAM_COLOURS[player_slot]);
            refreshFromSettings.run();
            on_color_changed.run();
        });

        ok_button.addMouseClickListener((@NonNull MouseButton button, int x, int y, int clicks) -> {
            Settings.getSettings().save();
            on_color_changed.run();
            if (on_ok_network_sync != null) {
                on_ok_network_sync.accept(new Vector4f(Settings.getSettings().team_colours[player_slot]));
            }
            remove();
        });

        refreshFromSettings.run();

        player_label.place();
        preview.place(player_label, RIGHT_MID);
        color_wheel.place(player_label, BOTTOM_LEFT);
        brightness_label.place(color_wheel, BOTTOM_LEFT);
        brightness_slider.place(brightness_label, RIGHT_MID);
        reset_button.place(brightness_slider, BOTTOM_MID);
        ok_button.place(reset_button, RIGHT_MID);

        content.compileCanvas();
        content.place();
        compileCanvas();
        centerPos();
    }
}
