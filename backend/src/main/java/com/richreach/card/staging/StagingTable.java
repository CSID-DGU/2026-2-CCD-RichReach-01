package com.richreach.card.staging;

import java.util.List;
import java.util.Map;

/**
 * CSV 한 개와 적재할 DB 테이블의 대응 관계.
 * CSV 컬럼명과 DB 컬럼명은 같다. 새 CSV/컬럼이 생기면 {@link StagingTables}의 목록만 고친다.
 *
 * @param csvFile CSV 파일명
 * @param table   적재할 테이블
 * @param keys    중복 판단 기준(테이블의 UNIQUE KEY와 같아야 한다). 같은 키가 이미 있으면 덮어쓴다(upsert)
 * @param columns 적재할 컬럼. CSV에 있는 컬럼 중 여기에 없는 것은 무시한다
 * @param types   문자열이 아닌 컬럼의 타입. 없으면 문자열
 */
record StagingTable(String csvFile, String table, List<String> keys, List<String> columns,
                    Map<String, ColumnType> types) {

    ColumnType typeOf(String column) {
        return types.getOrDefault(column, ColumnType.STRING);
    }

    enum ColumnType { STRING, INT, LONG, DECIMAL, BOOL, DATE }
}
