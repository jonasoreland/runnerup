package org.runnerup.common.util;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ValueModelTest {
    private ValueModel<TestObject> sut;

    @Before
    public void setUp() {
        sut = new ValueModel<>();
    }

    @Test
    public void shouldCallListenerWithNullAsOldValueWhenCallingSet() {
        TestObject newValue = new TestObject();

        ValueModel.ChangeListener<TestObject> listener = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener);

        sut.set(newValue);

        verify(listener).onValueChanged(sut, null, newValue);
    }

    @Test
    public void shouldCallListenerWithOldValueWhenCallingSet() {
        TestObject oldValue = new TestObject();
        TestObject newValue = new TestObject();

        ValueModel.ChangeListener<TestObject> listener = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener);

        sut.set(oldValue);
        sut.set(newValue);

        verify(listener).onValueChanged(sut, null, oldValue);
        verify(listener).onValueChanged(sut, oldValue, newValue);
    }

    @Test
    public void shouldNotCallListenerIfValueDidNotChange() {
        TestObject newValue = new TestObject();

        ValueModel.ChangeListener<TestObject> listener = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener);

        sut.set(newValue);
        sut.set(newValue);

        verify(listener).onValueChanged(sut, null, newValue);
        verify(listener, never()).onValueChanged(sut, newValue, newValue);
    }

    @Test
    public void shouldNotCallListenerIfValueIsNull() {
        ValueModel.ChangeListener<TestObject> listener = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener);

        sut.set(null);

        verify(listener, never()).onValueChanged(eq(sut), anyObject(), anyObject());
    }

    @Test
    public void shouldNotCallListenerIfListenerIsRemoved() {
        TestObject newValue = new TestObject();
        ValueModel.ChangeListener<TestObject> listener = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener);
        sut.unregisterChangeListener(listener);
        sut.set(newValue);

        verify(listener, never()).onValueChanged(eq(sut), anyObject(), anyObject());
    }

    @Test
    public void shouldReturnSetValue() {
        TestObject newValue = new TestObject();
        sut.set(newValue);

        assertThat(sut.get(), is(equalTo(newValue)));
    }

    @Test
    public void shouldReturnNullIfNoValueSet() {
        assertThat(sut.get(), is(nullValue()));
    }

    @Test
    public void shouldNotCallListenersIfClearIsCalled() {
        TestObject newValue = new TestObject();
        ValueModel.ChangeListener<TestObject> listener1 = mock(ValueModel.ChangeListener.class);
        ValueModel.ChangeListener<TestObject> listener2 = mock(ValueModel.ChangeListener.class);
        ValueModel.ChangeListener<TestObject> listener3 = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener1);
        sut.registerChangeListener(listener2);
        sut.registerChangeListener(listener3);

        sut.clearListeners();

        sut.set(newValue);

        verify(listener1, never()).onValueChanged(eq(sut), anyObject(), anyObject());
        verify(listener2, never()).onValueChanged(eq(sut), anyObject(), anyObject());
        verify(listener3, never()).onValueChanged(eq(sut), anyObject(), anyObject());
    }

    @Test
    public void shouldCallMultipleListeners() {
        TestObject newValue = new TestObject();
        ValueModel.ChangeListener<TestObject> listener1 = mock(ValueModel.ChangeListener.class);
        ValueModel.ChangeListener<TestObject> listener2 = mock(ValueModel.ChangeListener.class);
        ValueModel.ChangeListener<TestObject> listener3 = mock(ValueModel.ChangeListener.class);
        sut.registerChangeListener(listener1);
        sut.registerChangeListener(listener2);
        sut.registerChangeListener(listener3);

        sut.set(newValue);

        verify(listener1).onValueChanged(sut, null, newValue);
        verify(listener2).onValueChanged(sut, null, newValue);
        verify(listener3).onValueChanged(sut, null, newValue);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionIfListenerIsNullWhenRegister() {
        sut.registerChangeListener(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionIfListenerIsNullWhenUnregister() {
        sut.unregisterChangeListener(null);
    }

    @Test
    public void shouldGetValueSetInConstructor() {
        TestObject value = new TestObject();
        ValueModel<TestObject> valueModel = new ValueModel<>(value);

        assertThat(valueModel.get(), is(equalTo(value)));
    }

    private class TestObject {
        private final UUID random;

        public TestObject() {
            random = UUID.randomUUID();
        }

        public UUID getRandom() {
            return random;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestObject)) return false;

            TestObject that = (TestObject) o;

            return getRandom().equals(that.getRandom());

        }

        @Override
        public int hashCode() {
            return getRandom().hashCode();
        }

        @NonNull
        @Override
        public String toString() {
            return "TestObject{" +
                    "random=" + random +
                    '}';
        }
    }
}
