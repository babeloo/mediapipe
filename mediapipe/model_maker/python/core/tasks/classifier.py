# Copyright 2022 The MediaPipe Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Custom classifier."""

import os
from typing import Any, Callable, Optional, Sequence, Union

import tensorflow as tf

from mediapipe.model_maker.python.core import hyperparameters as hp
from mediapipe.model_maker.python.core.data import classification_dataset as classification_ds
from mediapipe.model_maker.python.core.data import dataset
from mediapipe.model_maker.python.core.tasks import custom_model
from mediapipe.model_maker.python.core.utils import model_util


class Classifier(custom_model.CustomModel):
  """An abstract base class that represents a TensorFlow classifier."""

  def __init__(self, model_spec: Any, label_names: Sequence[str],
               shuffle: bool):
    """Initializes a classifier with its specifications.

    Args:
        model_spec: Specification for the model.
        label_names: A list of label names for the classes.
        shuffle: Whether the dataset should be shuffled.
    """
    super(Classifier, self).__init__(model_spec, shuffle)
    self._label_names = label_names
    self._num_classes = len(label_names)
    self._model: tf.keras.Model = None
    self._optimizer: Union[str, tf.keras.optimizers.Optimizer] = None
    self._loss_function: Union[str, tf.keras.losses.Loss] = None
    self._metric_function: Union[str, tf.keras.metrics.Metric] = None
    self._callbacks: Sequence[tf.keras.callbacks.Callback] = None
    self._hparams: hp.BaseHParams = None
    self._history: tf.keras.callbacks.History = None

  # TODO: Integrate this into all Model Maker tasks.
  def _train_model(self,
                   train_data: classification_ds.ClassificationDataset,
                   validation_data: classification_ds.ClassificationDataset,
                   preprocessor: Optional[Callable[..., bool]] = None):
    """Trains the classifier model.

    Compiles and fits the tf.keras `_model` and records the `_history`.

    Args:
      train_data: Training data.
      validation_data: Validation data.
      preprocessor: An optional data preprocessor that can be used when
        generating a tf.data.Dataset.
    """
    tf.compat.v1.logging.info('Training the models...')
    if len(train_data) < self._hparams.batch_size:
      raise ValueError(
          f'The size of the train_data {len(train_data)} can\'t be smaller than'
          f' batch_size {self._hparams.batch_size}. To solve this problem, set'
          ' the batch_size smaller or increase the size of the train_data.')

    train_dataset = train_data.gen_tf_dataset(
        batch_size=self._hparams.batch_size,
        is_training=True,
        shuffle=self._shuffle,
        preprocess=preprocessor)
    self._hparams.steps_per_epoch = model_util.get_steps_per_epoch(
        steps_per_epoch=self._hparams.steps_per_epoch,
        batch_size=self._hparams.batch_size,
        train_data=train_data)
    train_dataset = train_dataset.take(count=self._hparams.steps_per_epoch)
    validation_dataset = validation_data.gen_tf_dataset(
        batch_size=self._hparams.batch_size,
        is_training=False,
        preprocess=preprocessor)
    self._model.compile(
        optimizer=self._optimizer,
        loss=self._loss_function,
        metrics=[self._metric_function])
    self._history = self._model.fit(
        x=train_dataset,
        epochs=self._hparams.epochs,
        validation_data=validation_dataset,
        callbacks=self._callbacks)

  def evaluate(self, data: dataset.Dataset, batch_size: int = 32) -> Any:
    """Evaluates the classifier with the provided evaluation dataset.

    Args:
        data: Evaluation dataset
        batch_size: Number of samples per evaluation step.

    Returns:
      The loss value and accuracy.
    """
    ds = data.gen_tf_dataset(
        batch_size, is_training=False, preprocess=self._preprocess)
    return self._model.evaluate(ds)

  def export_labels(self, export_dir: str, label_filename: str = 'labels.txt'):
    """Exports classification labels into a label file.

    Args:
      export_dir: The directory to save exported files.
      label_filename: File name to save labels model. The full export path is
        {export_dir}/{label_filename}.
    """
    if not tf.io.gfile.exists(export_dir):
      tf.io.gfile.makedirs(export_dir)

    label_filepath = os.path.join(export_dir, label_filename)
    tf.compat.v1.logging.info('Saving labels in %s', label_filepath)
    with tf.io.gfile.GFile(label_filepath, 'w') as f:
      f.write('\n'.join(self._label_names))
