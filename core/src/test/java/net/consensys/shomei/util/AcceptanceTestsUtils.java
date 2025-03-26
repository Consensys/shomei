package net.consensys.shomei.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;

public class AcceptanceTestsUtils {

    protected static final MediaType MEDIA_TYPE_JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final BesuNode besuNode;


    public AcceptanceTestsUtils(final BesuNode besuNode) {
        this.besuNode = besuNode;
        httpClient = new OkHttpClient();
    }

    public Call callRpcRequest(final String request) {
        return httpClient.newCall(
                new Request.Builder()
                        .url(besuNode.jsonRpcBaseUrl().get())
                        .post(RequestBody.create(request, MEDIA_TYPE_JSON))
                        .build());
    }

    public String createGetTrieLogRequest(final long blockNumber) {
        return "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"shomei_getTrieLog\",\n" +
                "    \"params\": ["+blockNumber+"],\n" +
                "    \"id\": 1\n" +
                "}";
    }
}
