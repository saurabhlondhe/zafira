/*******************************************************************************
 * Copyright 2013-2019 Qaprosoft (http://www.qaprosoft.com).
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.qaprosoft.zafira.services.services.application.integration.impl;

import com.qaprosoft.zafira.models.db.Setting;
import com.qaprosoft.zafira.services.services.application.integration.AbstractIntegration;
import com.qaprosoft.zafira.services.util.ElasticsearchResultHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.qaprosoft.zafira.models.db.Setting.Tool.ELASTICSEARCH;

@SuppressWarnings("rawtypes")
@Component
public class ElasticsearchService extends AbstractIntegration {

    private final String url;
    private final String user;
    private final String password;
    private RestHighLevelClient client;

    public ElasticsearchService(
            @Value("${zafira.elasticsearch.url}") String url,
            @Value("${zafira.elasticsearch.user}") String user,
            @Value("${zafira.elasticsearch.pass}") String password
    ) {
        super(ELASTICSEARCH);
        this.url = url;
        this.user = user;
        this.password = password;

        if (!StringUtils.isBlank(url)) {
            RestClientBuilder builder = getBuilder(url);
            if (builder == null) {
                return;
            }
            if (!StringUtils.isBlank(user) && !StringUtils.isBlank(password)) {
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
                builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
            }
            this.client = new RestHighLevelClient(builder);
        }
    }

    public Map<String, String> getScreenshotsInfo(String correlationId, String... indices) {
        Map<String, String> result = null;
        if (isClientInitialized()) {
            try {
                result = new HashMap<>();
                SearchResponse response = search(SearchBuilder.ALL, prepareCorrelationIdMap(correlationId), indices);
                String lastMessage = null;
                for (SearchHit hit : response.getHits().getHits()) {
                    if (ElasticsearchResultHelper.getMessage(hit) != null && ElasticsearchResultHelper.getHeaders(hit) == null) {
                        lastMessage = ElasticsearchResultHelper.getMessage(hit);
                    }
                    if (ElasticsearchResultHelper.getHeaders(hit) != null && ElasticsearchResultHelper.getAmazonPath(hit) != null) {
                        result.put(ElasticsearchResultHelper.getAmazonPath(hit), lastMessage);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot get screenshots from elasticsearch", e);
            }
        }
        return result;
    }

    public SearchResponse search(SearchBuilder searchBuilder, Map<String, String> map, String... indices) throws IOException {
        return search(searchBuilder.apply(map), indices);
    }

    public SearchResponse search(QueryBuilder queryBuilder, String... indices) throws IOException {
        SearchResponse result = null;
        if (isClientInitialized()) {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(queryBuilder);
            SearchRequest request = new SearchRequest();
            request.source(searchSourceBuilder).indices(indices);
            result = this.client.search(request, RequestOptions.DEFAULT);
        }
        return result;
    }

    public enum SearchBuilder {

        ALL(map -> {
            return QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("correlation-id", map.get("correlationId")));
        });

        private Function<Map<String, String>, QueryBuilder> builder;

        SearchBuilder(Function<Map<String, String>, QueryBuilder> builder) {
            this.builder = builder;
        }

        public QueryBuilder apply(Map<String, String> map) {
            return this.builder.apply(map);
        }
    }

    @Override
    public void init() {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    private static RestClientBuilder getBuilder(String path) {
        String prefix;
        HttpHost host;
        RestClientBuilder result = null;
        try {
            URL url = new URL(path);
            host = HttpHost.create(url.getHost());
            prefix = url.getPath();
            result = !StringUtils.isBlank(prefix) ? RestClient.builder(host).setPathPrefix(prefix) : RestClient.builder(host);
        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return result;
    }

    private static Map<String, String> prepareCorrelationIdMap(String correlationId) {
        return new HashMap<String, String>() {
            private static final long serialVersionUID = -4417816881742998170L;
            {
                put("correlationId", correlationId);
            }
        };
    }

    public List<Setting> getSettings() {
        return new ArrayList<Setting>() {
            private static final long serialVersionUID = 7140283430898343120L;
            {
                add(new Setting() {
                    private static final long serialVersionUID = 658548604106441383L;
                    {
                        setName("URL");
                        setValue(url);
                    }
                });
                add(new Setting() {
                    private static final long serialVersionUID = 6585486043214259383L;
                    {
                        setName("user");
                        setValue(user);
                    }
                });
                add(new Setting() {
                    private static final long serialVersionUID = 6585486425564259383L;
                    {
                        setName("password");
                        setValue(password);
                    }
                });
            }
        };
    }

    public boolean isClientInitialized() {
        return getClient().isPresent();
    }

    private Optional<RestHighLevelClient> getClient() {
        return Optional.ofNullable(this.client);
    }
}
