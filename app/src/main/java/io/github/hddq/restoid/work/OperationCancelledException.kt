package io.github.hddq.restoid.work

import kotlinx.coroutines.CancellationException

class OperationCancelledException(message: String) : CancellationException(message)
