package com.oddlabs.tt.form;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;

import com.oddlabs.tt.gui.CancelButton;
import com.oddlabs.tt.gui.Form;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.Group;
import com.oddlabs.tt.gui.HorizButton;
import com.oddlabs.tt.gui.LabelBox;

import static com.oddlabs.tt.gui.Placement.BOTTOM_MID;
import static com.oddlabs.tt.gui.Placement.RIGHT_MID;

import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.input.GameAction;
import com.oddlabs.tt.input.InputBinding;
import com.oddlabs.tt.input.InputEvent;
import com.oddlabs.tt.input.InputPhase;
import com.oddlabs.tt.input.Key;
import com.oddlabs.tt.input.KeyBindingConflicts;
import com.oddlabs.tt.input.Modifier;
import com.oddlabs.tt.render.Renderer;

public class KeyBindingDialog extends Form {
    private final @NonNull GameAction action;
    private final @NonNull Consumer<@NonNull Set<@NonNull InputBinding>> onBindingChosen;
    private final @NonNull GUIRoot guiRoot;

    public KeyBindingDialog(@NonNull GUIRoot guiRoot, @NonNull GameAction action,
            @NonNull Consumer<@NonNull Set<@NonNull InputBinding>> onBindingChosen) {
        this.guiRoot = guiRoot;
        this.action = action;
        this.onBindingChosen = onBindingChosen;

        String actionName;
        try {
            actionName = AbstractOptionsMenu.i18n("action." + action.name());
        } catch (Exception e) {
            actionName = action.name();
        }

        LabelBox info_label = new LabelBox("Press key for: " + actionName, Skin.getSkin().getEditFont(), 300);
        addChild(info_label);

        Group button_group = new Group();
        addChild(button_group);

        HorizButton clear_button = new HorizButton(AbstractOptionsMenu.i18n("btn_clear"), 80);
        clear_button.addMouseClickListener((_, _, _, _) -> {
            onBindingChosen.accept(Set.of());
            remove();
        });
        button_group.addChild(clear_button);

        HorizButton reset_button = new HorizButton(AbstractOptionsMenu.i18n("btn_reset"), 80);
        reset_button.addMouseClickListener((_, _, _, _) -> {
            onBindingChosen.accept(Renderer.getLocalInput().getInputManager().getDefaultBindings(action));
            remove();
        });
        button_group.addChild(reset_button);

        HorizButton cancel_button = new CancelButton(80);
        cancel_button.addMouseClickListener((_, _, _, _) -> cancel());
        button_group.addChild(cancel_button);

        // Place objects
        info_label.place();
        clear_button.place();
        reset_button.place(clear_button, RIGHT_MID);
        cancel_button.place(reset_button, RIGHT_MID);
        button_group.compileCanvas();
        button_group.place(info_label, BOTTOM_MID);

        compileCanvas();
        centerPos();
        setCanFocus(true);
    }

    @Override
    public void handleInput(@NonNull InputEvent event) {
        if (event.getPhase() == InputPhase.PRESSED) {
            Key key = event.getKeyCode();

            boolean isModifierKey = (key == Key.LSHIFT || key == Key.RSHIFT || key == Key.LCONTROL
                    || key == Key.RCONTROL || key == Key.LALT || key == Key.RALT || key == Key.LSUPER
                    || key == Key.RSUPER);

            if (!isModifierKey && key != null && key != Key.KEY_UNKNOWN) {
                var modifiers = EnumSet.noneOf(Modifier.class);
                if (event.isShiftDown()) modifiers.add(Modifier.SHIFT);
                if (event.isAltDown()) modifiers.add(Modifier.ALT);
                if (event.isControlDown()) modifiers.add(Modifier.CONTROL);
                if (event.isMetaDown()) modifiers.add(Modifier.META);
                InputBinding binding = new InputBinding(key, modifiers, action);

                GameAction conflict = KeyBindingConflicts.findConflict(action, binding,
                        Renderer.getLocalInput().getInputManager());
                if (conflict != null) {
                    String otherName;
                    try {
                        otherName = AbstractOptionsMenu.i18n("action." + conflict.name());
                    } catch (Exception e) {
                        otherName = conflict.name();
                    }
                    guiRoot.addModalForm(new SwapConflictForm(otherName, conflict, binding));
                    event.consume();
                    return;
                }

                onBindingChosen.accept(Set.of(binding));
                remove();
                event.consume();
                return;
            }

            // Consume all pressed events to prevent bleed-through
            event.consume();
            return;
        }
        super.handleInput(event);
    }

    // Gives this action the pressed key and gives the conflicting action this action's
    // previous bindings, minus the colliding one.
    private void swapBindings(@NonNull GameAction other, @NonNull InputBinding binding) {
        var manager = Renderer.getLocalInput().getInputManager();
        Set<InputBinding> theirs = new HashSet<>();
        for (InputBinding b : manager.getBindings(other)) {
            if (b.key() != binding.key() || !b.modifiers().equals(binding.modifiers())) {
                theirs.add(b);
            }
        }
        for (InputBinding b : manager.getBindings(action)) {
            theirs.add(new InputBinding(b.key(), b.modifiers(), other));
        }
        manager.setBindings(other, theirs);
        onBindingChosen.accept(Set.of(binding));
        remove();
    }

    private final class SwapConflictForm extends Form {
        SwapConflictForm(@NonNull String otherName, @NonNull GameAction other, @NonNull InputBinding binding) {
            LabelBox info_label = new LabelBox(AbstractOptionsMenu.i18n("conflict_swap_message", otherName),
                    Skin.getSkin().getEditFont(), 300);
            addChild(info_label);

            Group button_group = new Group();
            HorizButton swap_button = new HorizButton(AbstractOptionsMenu.i18n("btn_swap"), 80);
            swap_button.addMouseClickListener((_, _, _, _) -> {
                swapBindings(other, binding);
                remove();
            });
            button_group.addChild(swap_button);
            HorizButton cancel_button = new CancelButton(80);
            cancel_button.addMouseClickListener((_, _, _, _) -> cancel());
            button_group.addChild(cancel_button);
            swap_button.place();
            cancel_button.place(swap_button, RIGHT_MID);
            button_group.compileCanvas();
            addChild(button_group);

            info_label.place();
            button_group.place(info_label, BOTTOM_MID);

            compileCanvas();
            centerPos();
        }
    }
}
