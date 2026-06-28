{{/*
Labels communs Kubernetes (recommandations app.kubernetes.io/*).
Usage : {{ include "firstpay.labels" (dict "ctx" $ "name" $svc.name) }}
*/}}
{{- define "firstpay.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: firstpay-studio
app.kubernetes.io/managed-by: {{ .ctx.Release.Service }}
app.kubernetes.io/version: {{ .ctx.Values.global.imageTag | quote }}
helm.sh/chart: {{ .ctx.Chart.Name }}-{{ .ctx.Chart.Version }}
environment: {{ .ctx.Values.global.environment }}
app: {{ .name }}
{{- end -}}

{{/*
Labels de selection (immuables) — selectors et matchLabels uniquement.
*/}}
{{- define "firstpay.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app: {{ .name }}
{{- end -}}

{{/*
Image complete d'un service.
Usage : {{ include "firstpay.image" (dict "ctx" $ "name" $svc.name) }}
*/}}
{{- define "firstpay.image" -}}
{{- printf "%s/%s:%s" .ctx.Values.global.imageRegistry .name .ctx.Values.global.imageTag -}}
{{- end -}}
