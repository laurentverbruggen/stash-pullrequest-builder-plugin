package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.List;

/**
 * Paged response for a Stash REST API call
 *
 * @author Laurent Verbruggen
 */
public class StashPagedResponse<T> {

	private int size;
	private int limit;
	private boolean isLastPage;
	private List<T> values;
	private int start;
	private int nextPageStart;

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public boolean getIsLastPage() {
		return isLastPage;
	}

	public void setIsLastPage(boolean isLastPage) {
		this.isLastPage = isLastPage;
	}

	public List<T> getValues() {
		return values;
	}

	public void setValues(List<T> values) {
		this.values = values;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getNextPageStart() {
		return nextPageStart;
	}

	public void setNextPageStart(int nextPageStart) {
		this.nextPageStart = nextPageStart;
	}

}
