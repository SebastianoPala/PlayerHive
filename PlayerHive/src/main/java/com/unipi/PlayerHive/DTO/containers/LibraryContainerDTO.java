package com.unipi.PlayerHive.DTO.containers;

import com.unipi.PlayerHive.DTO.games.LibraryGameDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class LibraryContainerDTO {
    private List<LibraryGameDTO> library;

    private int numPages;

    private boolean isLastPage;
}
