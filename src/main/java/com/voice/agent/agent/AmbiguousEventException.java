package com.voice.agent.agent;

import com.voice.agent.model.vo.CalendarEventVO;

import java.util.List;

public class AmbiguousEventException extends IllegalArgumentException {
    private final List<CalendarEventVO> candidates;

    public AmbiguousEventException(List<CalendarEventVO> candidates) {
        super("找到多个匹配日程，请选择要操作的日程");
        this.candidates = candidates;
    }

    public List<CalendarEventVO> getCandidates() {
        return candidates;
    }
}
