<script setup lang="ts">
import Magnet from './vendor/Magnet.vue'

defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    padding?: number
    disabled?: boolean
    magnetStrength?: number
    activeTransition?: string
    inactiveTransition?: string
    wrapperClassName?: string
    innerClassName?: string
  }>(),
  {
    padding: 100,
    disabled: false,
    magnetStrength: 2,
    activeTransition: 'transform 0.3s ease-out',
    inactiveTransition: 'transform 0.5s ease-in-out',
    wrapperClassName: '',
    innerClassName: '',
  },
)
</script>

<template>
  <Magnet
    :padding="padding"
    :disabled="disabled"
    :magnet-strength="magnetStrength"
    :active-transition="activeTransition"
    :inactive-transition="inactiveTransition"
    :wrapper-class-name="wrapperClassName"
    :inner-class-name="innerClassName"
    v-bind="$attrs"
  >
    <slot />
  </Magnet>
</template>
