package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacePhotoService {

    private final GooglePlacePhotoClient googlePlacePhotoClient;

    public String getPhotoUrl(String photoName) {
        return googlePlacePhotoClient.getPhotoUri(photoName);
    }
}
