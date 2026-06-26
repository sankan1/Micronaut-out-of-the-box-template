package com.example.auth.oidc;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

import java.util.Map;

@Client(id = "oidc")
public interface OidcTokenClient {

    @Post(uri = "/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    OidcTokenResponse refresh(@Body Map<String, String> form);

    @Post(uri = "/protocol/openid-connect/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    void revoke(@Body Map<String, String> form);
}
