package com.example.controller;

import com.example.openapi.api.MathApi;
import com.example.openapi.model.SumResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;

@Controller
public class MathController implements MathApi {

    @Override
    public HttpResponse<@Valid SumResponse> sum(Long a, Long b) {
        return HttpResponse.ok(new SumResponse().sum(Math.addExact(a, b)));
    }
}
