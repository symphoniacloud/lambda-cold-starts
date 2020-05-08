package io.symphonia.lambdaColdStarts.query;

public class QueryParameters {
    public String invocationClass;
    public String trigger;

    public boolean isScheduledTrigger() {
        return trigger.equalsIgnoreCase("scheduled");
    }

    @Override
    public String toString() {
        return "QueryParameters{" +
                "invocationClass='" + invocationClass + '\'' +
                ", trigger='" + trigger + '\'' +
                '}';
    }
}
