package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PlacePhotoServiceTest {

    private final GooglePlacePhotoClient googlePlacePhotoClient = mock(GooglePlacePhotoClient.class);
    private final PlacePhotoService placePhotoService = new PlacePhotoService(googlePlacePhotoClient);

    @Test
    @DisplayName("photoName을 클라이언트에 위임해 photoUrl을 반환한다")
    void delegatesPhotoUriResolutionToClient() {
        given(googlePlacePhotoClient.getPhotoUri("places/ChIJ123/photos/abc"))
                .willReturn("https://lh3.googleusercontent.com/photo.jpg");

        String result = placePhotoService.getPhotoUrl("places/ChIJ123/photos/abc");

        assertThat(result).isEqualTo("https://lh3.googleusercontent.com/photo.jpg");
        then(googlePlacePhotoClient).should().getPhotoUri("places/ChIJ123/photos/abc");
    }
}
