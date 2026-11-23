package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.InstrumentSearchRepository;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentPage;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.RankedInstrument;
import com.cocos.challenge.domain.model.SearchCursor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("CU-02.12")
class SearchInstrumentsServiceTest {

    @Test
    void requestsOneExtraItemAndUsesTheLastReturnedItemForTheNextCursor() {
        FakeInstrumentSearchRepository repository = new FakeInstrumentSearchRepository(List.of(
                ranked(0, "AGRO"), ranked(0, "ALUA"), ranked(0, "AUSO")));
        SearchInstrumentsService service = new SearchInstrumentsService(repository);

        InstrumentPage page = service.search(criteria());

        assertThat(repository.maxResults).isEqualTo(3);
        assertThat(page.items()).extracting(InstrumentSummary::ticker).containsExactly("AGRO", "ALUA");
        assertThat(page.nextCursor()).isEqualTo(new SearchCursor(0, "ALUA"));
    }

    @Test
    void returnsNoCursorWhenThereIsNoNextPage() {
        FakeInstrumentSearchRepository repository = new FakeInstrumentSearchRepository(List.of(
                ranked(0, "AGRO"), ranked(0, "ALUA")));
        SearchInstrumentsService service = new SearchInstrumentsService(repository);

        InstrumentPage page = service.search(criteria());

        assertThat(repository.maxResults).isEqualTo(3);
        assertThat(page.items()).extracting(InstrumentSummary::ticker).containsExactly("AGRO", "ALUA");
        assertThat(page.nextCursor()).isNull();
    }

    private static InstrumentSearchCriteria criteria() {
        return InstrumentSearchCriteria.of(null, null, null, 2, null);
    }

    private static RankedInstrument ranked(int rank, String ticker) {
        return new RankedInstrument(rank, new InstrumentSummary(
                new InstrumentId(1), ticker, ticker + " name", InstrumentType.ACCIONES, null));
    }

    private static final class FakeInstrumentSearchRepository implements InstrumentSearchRepository {
        private final List<RankedInstrument> results;
        private int maxResults;

        private FakeInstrumentSearchRepository(List<RankedInstrument> results) {
            this.results = results;
        }

        @Override
        public List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults) {
            this.maxResults = maxResults;
            return results;
        }
    }
}
