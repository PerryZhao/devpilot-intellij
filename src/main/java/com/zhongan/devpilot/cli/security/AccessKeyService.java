package com.zhongan.devpilot.cli.security;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.zhongan.devpilot.agents.BinaryManager;
import com.zhongan.devpilot.util.LoginUtils;
import com.zhongan.devpilot.util.OkhttpUtils;
import com.zhongan.devpilot.util.UserAgentUtils;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import static com.zhongan.devpilot.constant.DefaultConst.REMOTE_RAG_DEFAULT_HOST;
import static com.zhongan.devpilot.constant.DefaultConst.RETRIEVE_CLI_AK;

/**
 * Service for managing access key retrieval
 */
public class AccessKeyService {
    private static final Logger LOG = Logger.getInstance(AccessKeyService.class);

    public static final AccessKeyService INSTANCE = new AccessKeyService();

    private volatile String cachedAccessKey;

    /**
     * Get access key, using cached value if available
     */
    public String getAccessKey() {
        if (StringUtils.isEmpty(cachedAccessKey)) {
            cachedAccessKey = retrieveAccessKey();
        }
        LOG.info("Getting accessKey:" + cachedAccessKey + ".");
        return cachedAccessKey;
    }

    /**
     * Refresh cached access key
     */
    public void refreshAccessKey() {
        cachedAccessKey = retrieveAccessKey();
    }

    /**
     * Clear cached access key
     */
    public void clearCache() {
        cachedAccessKey = null;
    }

    private String retrieveAccessKey() {
        try {
            Pair<Integer, Long> portPId = BinaryManager.INSTANCE.retrieveAlivePort();
            if (portPId == null) {
                LOG.warn("No alive port found for access key retrieval");
                return StringUtils.EMPTY;
            }

            String url = REMOTE_RAG_DEFAULT_HOST + portPId.first + RETRIEVE_CLI_AK;
            Request request = buildRequest(url);

            try (Response response = executeRequest(request)) {
                if (response.isSuccessful() && response.body() != null) {
                    String accessKey = response.body().string();
                    LOG.info("Successfully retrieved access key");
                    return accessKey;
                } else {
                    LOG.warn("Failed to fetch access key: HTTP " + response.code());
                }
            }
        } catch (IOException e) {
            LOG.error("Error fetching access key", e);
        } catch (Exception e) {
            LOG.error("Unexpected error during access key retrieval", e);
        }

        return StringUtils.EMPTY;
    }

    private Request buildRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", UserAgentUtils.buildUserAgent())
                .header("Auth-Type", LoginUtils.getLoginType())
                .build();
    }

    private Response executeRequest(Request request) throws IOException {
        Call call = OkhttpUtils.getClient().newCall(request);
        return call.execute();
    }
}
