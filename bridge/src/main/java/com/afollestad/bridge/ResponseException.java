package com.afollestad.bridge;

import android.support.annotation.NonNull;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ResponseException extends Exception {

    private Response mResponse;

    protected ResponseException(@NonNull Response response) {
        super(response.code() + " " + response.phrase());
        mResponse = response;
    }

    public Response getResponse() {
        return mResponse;
    }
}