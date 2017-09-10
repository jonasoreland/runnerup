/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.common.util;

import java.util.ArrayList;


public class ValueModel<T> {

    private T value;
    final private ArrayList<ChangeListener<T>> listeners =
            new ArrayList<>();

    public interface ChangeListener<T> {
        void onValueChanged(ValueModel<T> instance, T oldValue, T newValue);
    }

    public ValueModel() {
        value = null;
    }

    public ValueModel(T value) {
        this.value = value;
    }

    public void set(T newValue) {
        if (value == null && newValue == null) {
            return;
        } else if (value != null && newValue != null) {
            if (value.equals(newValue))
                return;
        }

        T oldValue = value;
        value = newValue;

        /*
         * iterate over copy so that this can be modified during iteration
         * (i.e by onValueChanged())
         */
        ArrayList<ChangeListener<T>> copy = new ArrayList<>(listeners);
        for (ChangeListener<T> l : copy) {
            l.onValueChanged(this, oldValue, newValue);
        }
    }

    public T get() {
        return value;
    }

    public void registerChangeListener(ChangeListener<T> listener) {
        if(listener == null) throw new IllegalArgumentException("listener is null");
        listeners.add(listener);
    }

    public void unregisterChangeListener(ChangeListener<T> listener) {
        if(listener == null) throw new IllegalArgumentException("listener is null");
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }
}
