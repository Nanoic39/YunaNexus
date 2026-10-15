{{/*
通用标签：可用于所有资源的 metadata.labels。
*/}}
{{- define "yunanexus.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ (include "yunanexus.imageTag" .) | quote }}
app.kubernetes.io/part-of: yunanexus
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/*
Service 选择器标签：不含 track —— stable 与 canary 两套 Pod 均被同一 Service 选中。
*/}}
{{- define "yunanexus.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Deployment 选择器标签：在 selectorLabels 基础上加 track，保证每个 Deployment 独占自己的 Pod。
传入 dict："root" -> $ ；"track" -> stable|canary
*/}}
{{- define "yunanexus.trackSelectorLabels" -}}
app.kubernetes.io/name: {{ .root.Chart.Name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
yunanexus.io/track: {{ .track }}
{{- end -}}

{{/*
镜像标签：canary.tag（金丝雀）> image.tag > Chart.AppVersion。
*/}}
{{- define "yunanexus.imageTag" -}}
{{- .Values.image.tag | default .Chart.AppVersion -}}
{{- end -}}

{{/*
容器规格：stable 与 canary 共用，避免两处漂移。
传入 dict："root" -> $ ；"track" -> stable|canary ；"tag" -> 镜像标签 ；"extraEnv" -> map（可空）
*/}}
{{- define "yunanexus.container" -}}
{{- $root := .root -}}
name: {{ $root.Chart.Name }}
image: "{{ $root.Values.image.repositoryPrefix }}/{{ $root.Release.Name }}:{{ .tag | default (include "yunanexus.imageTag" $root) }}"
imagePullPolicy: {{ $root.Values.image.pullPolicy }}
ports:
  - name: http
    containerPort: {{ $root.Values.service.port }}
    protocol: TCP
  - name: management
    containerPort: {{ $root.Values.service.managementPort }}
    protocol: TCP
env:
  - name: TZ
    value: {{ $root.Values.timezone | quote }}
  - name: SPRING_PROFILES_ACTIVE
    value: {{ $root.Values.springProfilesActive | quote }}
  - name: JAVA_OPTS
    value: {{ $root.Values.javaOpts | quote }}
  - name: YUNANEXUS_TRACK
    value: {{ .track | quote }}
  {{- range $k, $v := .extraEnv }}
  - name: {{ $k }}
    value: {{ $v | quote }}
  {{- end }}
envFrom:
  - configMapRef:
      name: {{ $root.Release.Name }}-config
  {{- if $root.Values.existingSecret }}
  - secretRef:
      name: {{ $root.Values.existingSecret }}
  {{- end }}
resources:
  {{- toYaml $root.Values.resources | nindent 2 }}
{{- with $root.Values.extraVolumeMounts }}
volumeMounts:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- if $root.Values.probes.enabled }}
startupProbe:
  httpGet:
    path: {{ $root.Values.probes.startup.path }}
    port: management
  initialDelaySeconds: {{ $root.Values.probes.startup.initialDelaySeconds }}
  periodSeconds: {{ $root.Values.probes.startup.periodSeconds }}
  timeoutSeconds: {{ $root.Values.probes.startup.timeoutSeconds }}
  failureThreshold: {{ $root.Values.probes.startup.failureThreshold }}
readinessProbe:
  httpGet:
    path: {{ $root.Values.probes.readiness.path }}
    port: management
  initialDelaySeconds: {{ $root.Values.probes.readiness.initialDelaySeconds }}
  periodSeconds: {{ $root.Values.probes.readiness.periodSeconds }}
  timeoutSeconds: {{ $root.Values.probes.readiness.timeoutSeconds }}
  failureThreshold: {{ $root.Values.probes.readiness.failureThreshold }}
livenessProbe:
  httpGet:
    path: {{ $root.Values.probes.liveness.path }}
    port: management
  initialDelaySeconds: {{ $root.Values.probes.liveness.initialDelaySeconds }}
  periodSeconds: {{ $root.Values.probes.liveness.periodSeconds }}
  timeoutSeconds: {{ $root.Values.probes.liveness.timeoutSeconds }}
  failureThreshold: {{ $root.Values.probes.liveness.failureThreshold }}
{{- end }}
{{- end -}}
