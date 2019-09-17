package com.symphony.ps.pollbot.services;

import java.util.Collections;
import java.util.List;
import model.FormButtonType;
import utils.FormBuilder;

public class PollService {
    public static String getPollML(boolean isLimitedAudience, int options, List<Integer> timeLimits) {
        FormBuilder formBuilder = new FormBuilder("poll-bot-form")
            .addHeader(6, "Question")
            .addTextArea("question", "", "Enter your poll question..", true)
            .addHeader(6, "Answers");

        for (int i=1; i <= options; i++) {
            boolean required = options <= 2;
            formBuilder = formBuilder.addTextField("option" + i, "", "Option " + i, required, false, 1, 50);
            if (i % 2 == 0 && i != options) {
                formBuilder = formBuilder.addLineBreak();
            }
        }

        if (isLimitedAudience) {
            formBuilder = formBuilder
                .addHeader(6, "Audience")
                .addPersonSelector("audience", "Select audience..", true);
        }

        formBuilder = formBuilder.addHeader(6, "Time Limit");
        Collections.sort(timeLimits);
        for (int i=0; i < timeLimits.size(); i++) {
            String timeLimit = timeLimits.get(i) + "";
            String display = timeLimit.equals("0") ? "None" : timeLimit + " minutes";
            formBuilder = formBuilder.addRadioButton("timeLimit", display, timeLimit, i == 0);
        }

        return formBuilder.addButton("createPoll", "Create Poll", FormButtonType.ACTION)
            .formatElement();
    }
}
