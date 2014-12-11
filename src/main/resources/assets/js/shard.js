var app = angular.module('main', ['ngTable']).
  controller('ShardCtrl', function($scope, $http, $interval, ngTableParams) {
    $scope.meta = {'shard': '(unknown shardid)',
                   'files': [],
                   'fingers': []};

    $scope.fileTableParams = new ngTableParams({
        page: 1,            // show first page
        count: 10           // count per page
    }, {
        total: $scope.meta.files.length, // length of data
        getData: function($defer, params) {
            $defer.resolve($scope.meta.files.slice((params.page() - 1) * params.count(), params.page() * params.count()));
        }
    });

    $scope.fingerTableParams = new ngTableParams({
        page: 1,            // show first page
        count: 50           // count per page
    }, {
        total: $scope.meta.fingers.length, // length of data
        getData: function($defer, params) {
            $defer.resolve($scope.meta.fingers.slice((params.page() - 1) * params.count(), params.page() * params.count()));
        }
    });

    $scope.refresh = function() {
        $http.get('/api/shard/meta').success(function(data, status, headers, config) {
            $scope.meta = data;
        });
    };

    $interval($scope.refresh, 1000);
});
