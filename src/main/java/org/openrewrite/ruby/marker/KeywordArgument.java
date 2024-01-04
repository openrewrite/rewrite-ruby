package org.openrewrite.ruby.marker;

import lombok.Value;
import lombok.With;
import org.openrewrite.marker.Marker;

import java.util.UUID;

@Value
@With
public class KeywordArgument implements Marker {
    UUID id;
}
