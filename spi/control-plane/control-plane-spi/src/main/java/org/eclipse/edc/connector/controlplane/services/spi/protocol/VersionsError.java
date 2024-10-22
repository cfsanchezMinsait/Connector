/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.connector.controlplane.services.spi.protocol;

import org.eclipse.edc.spi.types.domain.message.ErrorMessage;

/**
 * Represents an error message specific to the dps version requests .
 * This class extends the generic {@link ErrorMessage} to provide additional
 * context and functionality specific to version-related errors.
 */
public class VersionsError extends ErrorMessage {

    public static final class Builder extends ErrorMessage.Builder<VersionsError, Builder> {
        private Builder() {
            super(new VersionsError());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}