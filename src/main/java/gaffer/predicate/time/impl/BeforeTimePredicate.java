/**
 * Copyright 2015 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gaffer.predicate.time.impl;

import gaffer.predicate.time.TimePredicate;
import org.apache.hadoop.io.WritableUtils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Date;

/**
 * A predicate to indicate whether the end date is before the provided {@link Date}.
 */
public class BeforeTimePredicate extends TimePredicate {

    private static final long serialVersionUID = -2736981505231953784L;
    private Date time;

    public BeforeTimePredicate() { }

    public BeforeTimePredicate(Date time) {
        this.time = time;
    }

    @Override
    public boolean accept(Date startDate, Date endDate) {
        return endDate.compareTo(time) <= 0;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        WritableUtils.writeVLong(out, time.getTime());
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        time = new Date(WritableUtils.readVLong(in));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BeforeTimePredicate that = (BeforeTimePredicate) o;

        if (time != null ? !time.equals(that.time) : that.time != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return time != null ? time.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "BeforeTimePredicate{" +
                "time=" + time +
                '}';
    }
}
