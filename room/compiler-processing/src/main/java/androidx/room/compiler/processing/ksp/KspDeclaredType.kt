/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.compiler.processing.ksp

import androidx.room.compiler.processing.XDeclaredType
import androidx.room.compiler.processing.XType
import com.squareup.javapoet.TypeName
import org.jetbrains.kotlin.ksp.symbol.KSType

internal open class KspDeclaredType(
    env: KspProcessingEnv,
    ksType: KSType
) : KspType(env, ksType), XDeclaredType {
    override val typeName: TypeName by lazy {
        ksType.typeName()
    }

    override val typeArguments: List<XType> by lazy {
        ksType.arguments.mapIndexed { index, arg ->
            env.wrap(ksType.declaration.typeParameters[index], arg)
        }
    }
}
