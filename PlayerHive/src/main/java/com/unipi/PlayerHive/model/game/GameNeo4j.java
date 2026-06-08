package com.unipi.PlayerHive.model.game;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Game")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameNeo4j {
    @Id
    private String id;
    private String name;

    private Integer achievements;
    private String image;
}
