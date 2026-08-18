package com.cappielloantonio.tempo.util;

import static org.junit.Assert.assertEquals;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

@RunWith(JUnit4.class)
public class LiveDataUtilsTest {

    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    private static class TestLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry registry = new LifecycleRegistry(this);

        TestLifecycleOwner() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        }

        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }

    @Test
    public void observePlaylistSongsOnce_invokesActionExactlyOnce() {
        MutableLiveData<List<Child>> live = new MutableLiveData<>();

        PlaylistRepository fakeRepo = new PlaylistRepository() {
            @Override
            public MutableLiveData<List<Child>> getPlaylistSongs(String id) {
                return live;
            }
        };

        AtomicInteger called = new AtomicInteger(0);

        TestLifecycleOwner owner = new TestLifecycleOwner();

        LiveDataUtils.observePlaylistSongsOnce(fakeRepo, owner, "pl1", songs -> called.incrementAndGet());

        // First publish: should invoke action once
        live.setValue(Arrays.asList(new Child("1")));
        assertEquals(1, called.get());

        // Second publish: observer should have been removed, so count stays 1
        live.setValue(Arrays.asList(new Child("2")));
        assertEquals(1, called.get());
    }
}
