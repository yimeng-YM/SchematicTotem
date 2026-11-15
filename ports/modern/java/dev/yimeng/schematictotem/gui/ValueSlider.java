package dev.yimeng.schematictotem.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over [min, max] snapped to a step; the label shows the value and every move is applied at once. */
final class ValueSlider extends AbstractSliderButton {
    private final double min, max, step;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer setter;

    ValueSlider(int x, int y, int width, int height, double min, double max, double step, double value, DoubleFunction<Component> label,
                DoubleConsumer setter) {
        super(x, y, width, height, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.setter = setter;
        updateMessage();
    }

    double current() {
        double v = min + value * (max - min);
        if (step > 0) v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    void set(double v) {
        setValue((Math.max(min, Math.min(max, v)) - min) / (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(current()));
    }

    @Override
    protected void applyValue() {
        setter.accept(current());
    }
}
