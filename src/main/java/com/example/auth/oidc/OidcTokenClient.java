package com.example.auth.oidc;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

import java.util.Map;

@Client(id = "oidc")
public interface OidcTokenClient {

    @Post(uri = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    OidcTokenResponse refresh(@Body Map<String, String> form);

    @Post(uri = "/oauth2/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    void revoke(@Body Map<String, String> form);
}
