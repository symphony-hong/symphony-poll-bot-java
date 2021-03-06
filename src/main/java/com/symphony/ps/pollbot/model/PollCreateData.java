package com.symphony.ps.pollbot.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class PollCreateData extends PollData {
    private boolean showPersonSelector;
    private String targetStreamId;
    private int count;
    private List<Integer> timeLimits;
}
