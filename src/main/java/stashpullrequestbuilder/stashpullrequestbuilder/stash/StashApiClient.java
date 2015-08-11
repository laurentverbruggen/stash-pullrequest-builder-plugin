package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.client.utils.URIBuilder;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.transport.URIish;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * Created by Nathan McCarthy
 */
public class StashApiClient {
	private static final Logger logger = Logger.getLogger(StashApiClient.class.getName());

	private final String apiBaseUrl;
	private final String host;
	private final String project;
	private final String repositoryName;
	private final Credentials credentials;

	public StashApiClient(URIish stashUri, StandardUsernamePasswordCredentials credentials) {
		this(null, stashUri, credentials == null ? null : new UsernamePasswordCredentials(
				credentials.getUsername(),
				credentials.getPassword().getPlainText()));
	}

	public StashApiClient(String host, URIish stashUri, UsernamePasswordCredentials credentials) {
		// validate stash uri
		if (stashUri == null || !stashUri.getPath().startsWith("/scm/")) {
			throw new IllegalArgumentException("Invalid stash URI " + stashUri);
		}

		this.credentials = credentials;
		// split on / after removing prefix /scm/ should give the project name as first entry
		this.project = stashUri.getPath().substring(5).split("/")[0];
		this.repositoryName = stashUri.getHumanishName();
		// override host if provided
		this.host = host != null ? host : stashUri.getScheme() + "://" + stashUri.getHost() + (stashUri.getPort() != -1 ? ":" + stashUri.getPort() : "");
		this.apiBaseUrl = this.host + "/rest/api/1.0/projects/";
	}

	/* =============================================================== *
	 * 				           REQUEST HANDLING						   *
	 * =============================================================== */

	private HttpClient getHttpClient() {
		HttpClient client = new HttpClient();
		if (credentials != null)
			client.getState().setCredentials(AuthScope.ANY, credentials);
		return client;
	}

	private String sendRequest(HttpMethod method) {
		logger.log(Level.INFO, method.getName() + " " + method.getPath());
		HttpClient client = getHttpClient();
		client.getParams().setAuthenticationPreemptive(true);
		String response = null;
		int result = -1;
		try {
			result = client.executeMethod(method);
			response = method.getResponseBodyAsString();
		}
		catch (HttpException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		logger.log(Level.INFO, method.getName() + " " + method.getPath() + "\n  result: " + result + "\n  response: " + response);
		return response;
	}

	private <T> List<T> get(String path, Class<? extends StashPagedResponse<T>> clazz) throws JsonParseException, JsonMappingException, IOException,
			URISyntaxException {
		boolean isLastPage = false;
		int start = 0;
		List<T> result = null;
		while (!isLastPage) {
			URIBuilder uriBuilder = new URIBuilder(path);
			uriBuilder.addParameter("start", "" + start);
			String responseJson = sendRequest(new GetMethod(uriBuilder.build().toString()));
			StashPagedResponse<T> response = parseJson(responseJson, clazz);

			// initialise array if response was successfull
			if (result == null)
				result = new ArrayList<T>();

			// check if new call is required
			isLastPage = response.getIsLastPage();
			if (!isLastPage) {
				start = response.getNextPageStart();
			}

			// add all values to result
			result.addAll(response.getValues());
		}
		return result;
	}

	private void delete(String path) {
		sendRequest(new DeleteMethod(path));
	}

	private String post(String path, String body) throws JsonGenerationException, JsonMappingException, IOException {
		PostMethod httppost = new PostMethod(path);

		StringRequestEntity requestEntity = new StringRequestEntity(body, "application/json", "UTF-8");
		httppost.setRequestEntity(requestEntity);
		return sendRequest(httppost);
	}

	/* =============================================================== *
	 * 				          STASH API REQUESTS					   *
	 * =============================================================== */

	public List<StashPullRequestResponseValue> getPullRequests() {
		String path = pullRequestsPath() + "?state=OPEN";
		try {
			return get(path, StashPullRequestResponse.class);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "invalid GET pull requests response.", e);
		}
		return Collections.emptyList();
	}

	public List<StashPullRequestComment> getPullRequestComments(String pullRequestId) {
		String path = pullRequestPath(pullRequestId) + "/activities";
		try {
			List<StashPullRequestActivity> activities = get(path, StashPullRequestActivityResponse.class);

			if (activities != null) {
				List<StashPullRequestComment> comments = new ArrayList<StashPullRequestComment>();
				for (StashPullRequestActivity a : activities) {
					if (a != null && a.getComment() != null) comments.add(a.getComment());
				}
				return comments;
			}
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "invalid GET pull request comments response.", e);
		}
		return Collections.emptyList();
	}

	public void deletePullRequestComment(String pullRequestId, String commentId) {
		delete(pullRequestPath(pullRequestId) + "/comments/" + commentId + "?version=0");
	}

	public StashPullRequestComment postPullRequestComment(String pullRequestId, String comment) {
		String path = pullRequestPath(pullRequestId) + "/comments";
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode node = mapper.getNodeFactory().objectNode();
			node.put("text", comment);

			String response = post(path, mapper.writeValueAsString(node));
			return parseJson(response, StashPullRequestComment.class);

		}
		catch (Exception e) {
			logger.log(Level.WARNING, "invalid POST pull request comment response.", e);
		}
		return null;
	}

	public StashPullRequestMergableResponse getPullRequestMergeStatus(String pullRequestId) {
		String path = pullRequestPath(pullRequestId) + "/merge";
		try {
			String responseJson = sendRequest(new GetMethod(path));
			return parseJson(responseJson, StashPullRequestMergableResponse.class);

		}
		catch (Exception e) {
			logger.log(Level.WARNING, "invalid GET pull request merge status response.", e);
		}
		return null;
	}

	/* friendly */ static <T> T parseJson(String json, Class<T> clazz) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		T parsedResponse = mapper.readValue(json, clazz);
		return parsedResponse;
	}

	private String pullRequestsPath() {
		return apiBaseUrl + this.project + "/repos/" + this.repositoryName + "/pull-requests/";
	}

	private String pullRequestPath(String pullRequestId) {
		return pullRequestsPath() + pullRequestId;
	}

	public String getHost() {
		return host;
	}

	public String getProject() {
		return project;
	}

	public String getRepositoryName() {
		return repositoryName;
	}
}
