<script setup>
// 通用 ECharts 容器：管理后台看板用。
// 按需引入 line/bar/pie，避免把整包 echarts 打进 bundle。
// option 由父组件计算，本组件只负责初始化、更新、resize 与销毁。
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  option: { type: Object, default: () => ({}) },
  height: { type: String, default: '280px' },
})

const el = ref(null)
const chart = shallowRef(null)

function render() {
  if (chart.value) {
    // notMerge：序列数变化时不留残余
    chart.value.setOption(props.option || {}, { notMerge: true })
  }
}

function resize() {
  chart.value?.resize()
}

onMounted(() => {
  chart.value = echarts.init(el.value, null, { renderer: 'canvas' })
  render()
  window.addEventListener('resize', resize)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart.value?.dispose()
  chart.value = null
})
</script>

<template>
  <div ref="el" :style="{ width: '100%', height }"></div>
</template>
