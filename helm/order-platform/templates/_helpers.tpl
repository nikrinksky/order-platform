{{/*
Common template helpers.
*/}}

{{/* Chart name, used as a prefix for the object names. */}}
{{- define "order-platform.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{/* Labels put on every object, so `kubectl get all -l app.kubernetes.io/part-of=...` works. */}}
{{- define "order-platform.labels" -}}
app.kubernetes.io/part-of: order-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{/*
Labels that identify one workload. Called with a dict: (dict "context" $ "component" "auth-service").
*/}}
{{- define "order-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
{{- end -}}

{{/*
Fully qualified image reference. Called with a dict: (dict "context" $ "image" "postgres:15-alpine")
or with a service name for the platform images.
*/}}
{{- define "order-platform.image" -}}
{{- if .isApp -}}
{{- printf "%s%s:%s" .context.Values.images.repositoryPrefix .name .context.Values.images.tag -}}
{{- else -}}
{{- .name -}}
{{- end -}}
{{- end -}}

{{/*
Environment shared by every Spring service: the profile that resolves the infrastructure names,
the JWT secret and the OTLP endpoint. The secret reference is optional on purpose - the pods then
start on a fresh cluster before `make inject-secrets` has run.
*/}}
{{- define "order-platform.springEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .Values.springProfile | quote }}
- name: JWT_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.name }}
      key: JWT_SECRET
      optional: true
- name: ADMIN_EMAIL
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.name }}
      key: ADMIN_EMAIL
      optional: true
- name: ADMIN_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.name }}
      key: ADMIN_PASSWORD
      optional: true
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: {{ .Values.tracing.endpoint | quote }}
{{- end -}}
