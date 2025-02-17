// Copyright 2022 The MediaPipe Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.tasks.audio.core;

import com.google.mediapipe.framework.MediaPipeException;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.tasks.components.containers.AudioData;
import com.google.mediapipe.tasks.core.TaskResult;
import com.google.mediapipe.tasks.core.TaskRunner;
import java.util.HashMap;
import java.util.Map;

/** The base class of MediaPipe audio tasks. */
public class BaseAudioTaskApi implements AutoCloseable {
  private static final long MICROSECONDS_PER_MILLISECOND = 1000;
  private static final long PRESTREAM_TIMESTAMP = Long.MIN_VALUE + 2;

  private final TaskRunner runner;
  private final RunningMode runningMode;
  private final String audioStreamName;
  private final String sampleRateStreamName;
  private double defaultSampleRate;

  static {
    System.loadLibrary("mediapipe_tasks_audio_jni");
  }

  /**
   * Constructor to initialize a {@link BaseAudioTaskApi}.
   *
   * @param runner a {@link TaskRunner}.
   * @param runningMode a mediapipe audio task {@link RunningMode}.
   * @param audioStreamName the name of the input audio stream.
   * @param sampleRateStreamName the name of the audio sample rate stream.
   */
  public BaseAudioTaskApi(
      TaskRunner runner,
      RunningMode runningMode,
      String audioStreamName,
      String sampleRateStreamName) {
    this.runner = runner;
    this.runningMode = runningMode;
    this.audioStreamName = audioStreamName;
    this.sampleRateStreamName = sampleRateStreamName;
    this.defaultSampleRate = -1.0;
  }

  /**
   * A synchronous method to process audio clips. The call blocks the current thread until a failure
   * status or a successful result is returned.
   *
   * @param audioClip a MediaPipe {@link AudioDatra} object for processing.
   * @throws MediaPipeException if the task is not in the audio clips mode.
   */
  protected TaskResult processAudioClip(AudioData audioClip) {
    if (runningMode != RunningMode.AUDIO_CLIPS) {
      throw new MediaPipeException(
          MediaPipeException.StatusCode.FAILED_PRECONDITION.ordinal(),
          "Task is not initialized with the audio clips mode. Current running mode:"
              + runningMode.name());
    }
    Map<String, Packet> inputPackets = new HashMap<>();
    inputPackets.put(
        audioStreamName,
        runner
            .getPacketCreator()
            .createMatrix(
                audioClip.getFormat().getNumOfChannels(),
                audioClip.getBufferLength(),
                audioClip.getBuffer()));
    inputPackets.put(
        sampleRateStreamName,
        runner.getPacketCreator().createFloat64(audioClip.getFormat().getSampleRate()));
    return runner.process(inputPackets);
  }

  /**
   * Checks or sets the audio sample rate in the audio stream mode.
   *
   * @param sampleRate the audio sample rate.
   * @throws MediaPipeException if the task is not in the audio stream mode or the provided sample
   *     rate is inconsistent with the previously received.
   */
  protected void checkOrSetSampleRate(double sampleRate) {
    if (runningMode != RunningMode.AUDIO_STREAM) {
      throw new MediaPipeException(
          MediaPipeException.StatusCode.FAILED_PRECONDITION.ordinal(),
          "Task is not initialized with the audio stream mode. Current running mode:"
              + runningMode.name());
    }
    if (defaultSampleRate > 0) {
      if (Double.compare(sampleRate, defaultSampleRate) != 0) {
        throw new MediaPipeException(
            MediaPipeException.StatusCode.INVALID_ARGUMENT.ordinal(),
            "The input audio sample rate: "
                + sampleRate
                + " is inconsistent with the previously provided: "
                + defaultSampleRate);
      }
    } else {
      Map<String, Packet> inputPackets = new HashMap<>();
      inputPackets.put(sampleRateStreamName, runner.getPacketCreator().createFloat64(sampleRate));
      runner.send(inputPackets, PRESTREAM_TIMESTAMP);
      defaultSampleRate = sampleRate;
    }
  }
  /**
   * An asynchronous method to send audio stream data to the {@link TaskRunner}. The results will be
   * available in the user-defined result listener.
   *
   * @param audioClip a MediaPipe {@link AudioDatra} object for processing.
   * @param timestampMs the corresponding timestamp of the input image in milliseconds.
   * @throws MediaPipeException if the task is not in the stream mode.
   */
  protected void sendAudioStreamData(AudioData audioClip, long timestampMs) {
    if (runningMode != RunningMode.AUDIO_STREAM) {
      throw new MediaPipeException(
          MediaPipeException.StatusCode.FAILED_PRECONDITION.ordinal(),
          "Task is not initialized with the audio stream mode. Current running mode:"
              + runningMode.name());
    }
    Map<String, Packet> inputPackets = new HashMap<>();
    inputPackets.put(
        audioStreamName,
        runner
            .getPacketCreator()
            .createMatrix(
                audioClip.getFormat().getNumOfChannels(),
                audioClip.getBufferLength(),
                audioClip.getBuffer()));
    runner.send(inputPackets, timestampMs * MICROSECONDS_PER_MILLISECOND);
  }

  /** Closes and cleans up the MediaPipe audio task. */
  @Override
  public void close() {
    runner.close();
  }
}
