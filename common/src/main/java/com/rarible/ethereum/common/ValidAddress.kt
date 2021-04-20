package com.rarible.ethereum.common

import java.util.regex.Pattern
import javax.validation.Constraint
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext
import javax.validation.Payload
import kotlin.reflect.KClass

@Retention
@MustBeDocumented
@Constraint(validatedBy = [ValidAddress.Validator::class])
annotation class ValidAddress(
    val message: String = "Invalid address",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
) {
    class Validator : ConstraintValidator<ValidAddress, String> {
        private companion object {
            val ADDRESS_FORMAT: Pattern = Pattern.compile("^(0x)?[0-9a-fA-F]{40}\$")
        }

        override fun isValid(address: String?, context: ConstraintValidatorContext): Boolean {
            if (address == null) {
                return false
            }
            val matcher = ADDRESS_FORMAT.matcher(address)

            context.disableDefaultConstraintViolation()

            return if (matcher.find().not()) {
                context
                    .buildConstraintViolationWithTemplate("Address '${address}' is not valid format")
                    .addConstraintViolation()
                false
            } else {
                true
            }
        }
    }
}
