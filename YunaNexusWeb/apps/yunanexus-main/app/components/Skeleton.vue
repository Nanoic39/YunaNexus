<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    type: "card" | "text" | "circle" | "list";
    count?: number;
    width?: string;
    height?: string;
  }>(),
  {
    count: 1,
  },
);
</script>

<template>
  <div class="skeleton-wrapper">
    <!-- card -->
    <template v-if="type === 'card'">
      <div
        v-for="i in count"
        :key="i"
        class="skeleton-block skeleton-shimmer"
        :style="{ width: width || '100%', height: height || '120px' }"
      />
    </template>

    <!-- text -->
    <template v-else-if="type === 'text'">
      <div
        v-for="i in count"
        :key="i"
        class="skeleton-block skeleton-shimmer"
        :style="{ width: width || '100%', height: height || '14px' }"
      />
    </template>

    <!-- circle -->
    <template v-else-if="type === 'circle'">
      <div
        v-for="i in count"
        :key="i"
        class="skeleton-block skeleton-shimmer skeleton-circle"
        :style="{ width: width || '40px', height: height || '40px' }"
      />
    </template>

    <!-- list -->
    <template v-else-if="type === 'list'">
      <div
        v-for="i in count"
        :key="i"
        class="skeleton-block skeleton-shimmer"
        :style="{
          width: width || `calc(100% - ${(i - 1) * 8}%)`,
          height: height || '14px',
        }"
      />
    </template>
  </div>
</template>

<style scoped>
.skeleton-wrapper {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-block {
  border-radius: var(--radius-md);
  background: var(--color-primary-background);
}

.skeleton-circle {
  border-radius: 50%;
}

.skeleton-shimmer {
  background: linear-gradient(
    90deg,
    var(--color-primary-background) 25%,
    var(--color-border) 50%,
    var(--color-primary-background) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
