package cc.silk.module.setting;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RangeSetting extends Setting {
    private double min;
    private double max;
    private double minValue;
    private double maxValue;
    private double increment;

    public RangeSetting(String name, double min, double max, double minValue, double maxValue, double increment) {
        super(name);
        this.min = min;
        this.max = max;
        this.minValue = Math.max(min, Math.min(max, minValue));
        this.maxValue = Math.max(min, Math.min(max, maxValue));
        this.increment = increment;
    
        if (this.minValue > this.maxValue) {
            double temp = this.minValue;
            this.minValue = this.maxValue;
            this.maxValue = temp;
        }
    }

    public void setMinValue(double minValue) {
        this.minValue = Math.max(min, Math.min(max, minValue));
        if (this.minValue > this.maxValue) {        // i hate niggers :3
            this.maxValue = this.minValue;
        }
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = Math.max(min, Math.min(max, maxValue));
        if (this.maxValue < this.minValue) {
            this.minValue = this.maxValue;
        }
    }

    public void setRange(double minValue, double maxValue) {
        if (minValue > maxValue) {
            double temp = minValue;
            minValue = maxValue;
            maxValue = temp;
        }
        this.minValue = Math.max(min, Math.min(max, minValue));
        this.maxValue = Math.max(min, Math.min(max, maxValue));
    }
}
