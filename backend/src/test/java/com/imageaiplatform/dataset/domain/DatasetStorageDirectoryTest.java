package com.imageaiplatform.dataset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatasetStorageDirectoryTest {
    @Test
    void expandsDistinctAndRepeatedTokensWithLiteralValues() {
        var tokens = Map.of("dataset_id", "d1", "aaa", "$1", "bbb", "value");

        assertThat(DatasetStorageDirectory.expand("{dataset_id}/{aaa}-{bbb}/{aaa}", tokens))
                .isEqualTo("d1/$1-value/$1");
        assertThatThrownBy(() -> DatasetStorageDirectory.expand("{aaa}/{unknown}", tokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{unknown}");
    }
}
