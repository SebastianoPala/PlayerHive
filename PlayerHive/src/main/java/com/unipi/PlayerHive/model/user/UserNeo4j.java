package com.unipi.PlayerHive.model.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("User")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNeo4j {
    @Id
    private String id;
    private String username;
    private String pfpURL;
}
