package com.cocos.challenge.domain.model;

import java.util.List;

/** One page of a cursor-paginated list (CM-08): its items and the cursor of the next page, null on the last one. */
public record CursorPage<T, C>(List<T> items, C nextCursor) {
    public CursorPage {
        items = List.copyOf(items);
    }
}
