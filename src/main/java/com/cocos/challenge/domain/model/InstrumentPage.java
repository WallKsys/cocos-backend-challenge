package com.cocos.challenge.domain.model;

import java.util.List;

public record InstrumentPage(List<InstrumentSummary> items, SearchCursor nextCursor) {
}
