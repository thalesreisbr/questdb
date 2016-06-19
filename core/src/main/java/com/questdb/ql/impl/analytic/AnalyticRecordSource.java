/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2016 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.ql.impl.analytic;

import com.questdb.ex.JournalException;
import com.questdb.factory.JournalReaderFactory;
import com.questdb.factory.configuration.RecordMetadata;
import com.questdb.ql.*;
import com.questdb.ql.impl.CollectionRecordMetadata;
import com.questdb.ql.impl.SplitRecordMetadata;
import com.questdb.ql.ops.AbstractCombinedRecordSource;
import com.questdb.std.CharSink;
import com.questdb.std.ObjList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class AnalyticRecordSource extends AbstractCombinedRecordSource {
    private final RecordSource parentSource;
    private final ObjList<AnalyticFunction> functions;
    private final RecordMetadata metadata;
    private final AnalyticRecord record;
    private final AnalyticRecordStorageFacade storageFacade;
    private RecordCursor parentCursor;

    public AnalyticRecordSource(RecordSource parentSource, ObjList<AnalyticFunction> functions) {
        this.parentSource = parentSource;
        this.functions = functions;

        CollectionRecordMetadata funcMetadata = new CollectionRecordMetadata();
        for (int i = 0; i < functions.size(); i++) {
            funcMetadata.add(functions.getQuick(i).getMetadata());
        }
        this.metadata = new SplitRecordMetadata(parentSource.getMetadata(), funcMetadata);
        int split = parentSource.getMetadata().getColumnCount();
        this.record = new AnalyticRecord(split, functions);
        this.storageFacade = new AnalyticRecordStorageFacade(split, functions);
    }

    @Override
    public RecordMetadata getMetadata() {
        return metadata;
    }

    @Override
    public RecordCursor prepareCursor(JournalReaderFactory factory, CancellationHandler cancellationHandler) throws JournalException {
        this.parentCursor = this.parentSource.prepareCursor(factory, cancellationHandler);
        final StorageFacade storageFacade = parentCursor.getStorageFacade();
        this.storageFacade.prepare(factory, storageFacade);
        int n = functions.size();
        for (int i = 0; i < n; i++) {
            functions.getQuick(i).prepare(this.parentCursor);
        }
        return this;
    }

    @Override
    public void reset() {
        parentSource.reset();
        for (int i = 0, n = functions.size(); i < n; i++) {
            functions.getQuick(i).reset();
        }
    }

    @Override
    public boolean supportsRowIdAccess() {
        return false;
    }

    @Override
    public StorageFacade getStorageFacade() {
        return storageFacade;
    }

    @Override
    public Record newRecord() {
        return null;
    }

    @Override
    public Record recordAt(long rowId) {
        return null;
    }

    @Override
    public void recordAt(Record record, long atRowId) {
    }

    @Override
    public boolean hasNext() {
        if (parentCursor.hasNext()) {
            record.of(parentCursor.next());
            for (int i = 0, n = functions.size(); i < n; i++) {
                functions.getQuick(i).scroll(record);
            }
            return true;
        }
        return false;
    }

    @SuppressFBWarnings("IT_NO_SUCH_ELEMENT")
    @Override
    public Record next() {
        return record;
    }

    @Override
    public void toSink(CharSink sink) {

    }
}
