<script setup lang="ts">
import Magnet from './vendor/Magnet.vue'
import { useReducedMotion } from '../../composables/useReducedMotion'

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

const reduced = useReducedMotion()
</script>

<template>
  <!--
    减少动态效果：不挂 vendor/Magnet（它会注册全局 mousemove + rAF），
    但仍渲染同构的容器，保证布局与 wrapperClassName/innerClassName 语义不变。
  -->
  <div
    v-if="reduced"
    :class="wrapperClassName"
    :style="{ position: 'relative', display: 'inline-block' }"
    v-bind="$attrs"
  >
    <div :class="innerClassName">
      <slot />
    </div>
  </div>
  <Magnet
    v-else
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
